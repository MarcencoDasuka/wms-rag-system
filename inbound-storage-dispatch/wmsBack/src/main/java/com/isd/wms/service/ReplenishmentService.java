package com.isd.wms.service;

import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.dto.replenishment.ReplenishmentSearchRequest;
import com.isd.wms.dto.replenishment.ReplenishmentUpdateRequest;
import com.isd.wms.dto.replenishment.shortage.AffectedReplenishmentLineResponse;
import com.isd.wms.dto.replenishment.shortage.ShortageReplenishmentDetailsResponse;
import com.isd.wms.dto.replenishment.shortage.ShortageReplenishmentResponse;
import com.isd.wms.entity.*;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskStatus;
import com.isd.wms.enums.TaskType;
import com.isd.wms.event.LowStockEvent;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.exception.LocationNotFoundException;
import com.isd.wms.exception.ProductNotFoundException;
import com.isd.wms.exception.ReplenishmentNotFoundException;
import com.isd.wms.mapper.ReplenishmentMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.imports.dto.ReplenishmentInfo;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing replenishment tasks.
 * <p>
 * Replenishments ensure that picking locations maintain sufficient stock.
 * The service allows creation, update, cancellation, and assignment of
 * replenishment tasks. It also supports automatic replenishment based on
 * product thresholds and current stock levels.
 * </p>
 * <p>
 * When a replenishment is assigned, a corresponding {@link Task} is created
 * and allocated to an operator. Cancelling releases any reserved stock and
 * disassociates any transport unit.
 * </p>
 *
 * @see Replenishment
 * @see Task
 * @see Allocation
 * @see Stock
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplenishmentService {

    private final ReplenishmentRepository replenishmentRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final AllocationRepository allocationRepository;
    private final TransportUnitRepository transportUnitRepository;
    private final ReplenishmentMapper replenishmentMapper;
    private final WorkflowService workflowService;
    private final TaskService taskService;
    private final ImportService importService;
    private final SecurityFacade securityFacade;

    private static final List<Status> ACTIVE_STATUSES = List.of(Status.CREATED, Status.ASSIGNED, Status.IN_PROGRESS);

    private void validateDestinationLocation(Product incomingProduct, Location destinationLocation) {
        stockRepository.findByLocationIdAndAvailableIsTrue(destinationLocation.getId())
            .ifPresent(stock -> {
                Product existingProduct = stock.getProduct().orElse(null);
                if (existingProduct != null && !existingProduct.getId().equals(incomingProduct.getId())) {
                    throw new InvalidRequestException(
                        "Cannot route replenishment to " + destinationLocation.getBarcode() +
                            ". Location is already occupied by a different product: " + existingProduct.getName()
                    );
                }
            });
    }

    /**
     * Creates a new replenishment request.
     *
     * @param request the creation request
     * @return the created replenishment response
     * @throws InvalidRequestException if the destination location is already occupied by a different product
     */
    @Transactional
    public ReplenishmentResponse createReplenishment(ReplenishmentCreateRequest request) {
        log.info("Creating replenishment: productId={}, requestedQuantity={}, destinationLocationId={}",
            request.productId(), request.requestedQuantity(), request.destinationLocationId());

        Product product = getProduct(request.productId());
        Location destinationLocation = getLocation(request.destinationLocationId());

        validateDestinationLocation(product, destinationLocation);

        Replenishment replenishment = new Replenishment(product, request.requestedQuantity(), destinationLocation);
        replenishment.setStatus(Status.CREATED);

        replenishment.setLogicId("REPL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        replenishment = replenishmentRepository.save(replenishment);

        return replenishmentMapper.toResponse(replenishment);
    }

    @Transactional
    public ReplenishmentResponse updateReplenishment(Long id, ReplenishmentUpdateRequest request) {
        log.info("Updating replenishment: id={}, status={}", id, request.status());

        Replenishment replenishment = getReplenishment(id);

        if (replenishment.getStatus() == Status.IN_PROGRESS ||
            replenishment.getStatus() == Status.COMPLETED ||
            replenishment.getStatus() == Status.CANCELED) {
            throw new InvalidRequestException("Cannot update replenishment that is currently in status: " +
                replenishment.getStatus().name());
        }

        Product product = getProduct(request.productId());
        Location destinationLocation = getLocation(request.destinationLocationId());

        validateDestinationLocation(product, destinationLocation);

        boolean isProductChanged = !request.productId().equals(replenishment.getProduct().getId());
        boolean isQuantityChanged = !request.requestedQuantity().equals(replenishment.getRequestedQuantity());

        if (isProductChanged || isQuantityChanged) {
            replenishment.getTask().ifPresent(task -> {
                task.setRequestedQuantity(request.requestedQuantity());
                workflowService.updateTask(task, request.productId(), request.requestedQuantity());
            });
        }

        updateReplenishment(request, replenishment, product, destinationLocation);

        return replenishmentMapper.toResponse(replenishmentRepository.save(replenishment));
    }

    private static void updateReplenishment(
        ReplenishmentUpdateRequest request,
        Replenishment replenishment,
        Product product,
        Location destinationLocation) {
        replenishment.setProduct(product);
        replenishment.setRequestedQuantity(request.requestedQuantity());

        if (request.status() != null) {
            replenishment.setStatus(request.status());
        }

        replenishment.setDestinationLocation(destinationLocation);
    }

    /**
     * Automatically triggers a replenishment if stock at a location falls below
     * the product's minimum threshold and no active replenishment exists.
     *
     * @param product     the product to replenish
     * @param location    the picking location
     * @param locationQty the current available quantity at the location
     */
    @Transactional
    public void checkAndTriggerAutoReplenishment(Product product, Location location, int locationQty) {
        if (!Boolean.TRUE.equals(product.getAutoReplenish())) return;

        if (product.getReplenishQty().isEmpty() || product.getMinThreshold().isEmpty()) return;

        int minThreshold = product.getMinThreshold().get();
        int replenishQty = product.getReplenishQty().get();

        if (locationQty <= minThreshold) {
            boolean hasActive = replenishmentRepository.existsByProductIdAndDestinationLocationIdAndStatusIn(
                product.getId(), location.getId(), ACTIVE_STATUSES
            );

            if (!hasActive) {
                log.info("Auto-triggering replenishment for product {} at location {} (Location Qty: {}, Threshold: {})",
                    product.getBarcode(), location.getBarcode(), locationQty, minThreshold);

                ReplenishmentCreateRequest req = new ReplenishmentCreateRequest(
                    product.getId(), replenishQty, location.getId()
                );
                createReplenishment(req);
            }
        }
    }

    @EventListener
    public void handleLowStockEvent(LowStockEvent event) {
        checkAndTriggerAutoReplenishment(event.product(), event.location(), event.availableQuantity());
    }

    @Transactional
    public void deleteReplenishment(Long replenishmentId) {
        log.info("Deleting replenishment: id={}", replenishmentId);
        Replenishment replenishment = getReplenishment(replenishmentId);

        if (replenishment.getStatus() != Status.CREATED) {
            throw new InvalidRequestException("Physical deletion is only allowed for tasks in CREATED status.");
        }

        replenishment.getTask().ifPresent(task -> {
            List<Allocation> allocations = allocationRepository.findAllByTaskId(task.getId());
            for (Allocation allocation : allocations) {
                Stock stock = allocation.getStock();
                stock.setReservedQuantity(stock.getReservedQuantity() - allocation.getQuantity());
                stockRepository.save(stock);
            }
            allocationRepository.deleteAll(allocations);
        });

        replenishmentRepository.delete(replenishment);
    }

    /**
     * Cancels a replenishment. Releases reserved stock and disassociates any transport unit.
     *
     * @param replenishmentId the ID of the replenishment to cancel
     * @return the updated replenishment response (status CANCELED)
     * @throws InvalidRequestException if the replenishment is already COMPLETED or CANCELED
     */
    @Transactional
    public ReplenishmentResponse cancelReplenishment(Long replenishmentId) {
        log.info("Canceling replenishment: id={}", replenishmentId);
        Replenishment replenishment = getReplenishment(replenishmentId);

        if (replenishment.getStatus() == Status.COMPLETED || replenishment.getStatus() == Status.CANCELED) {
            throw new InvalidRequestException("Cannot cancel a task that is already COMPLETED or CANCELED.");
        }

        transportUnitRepository.findAllByReplenishment(replenishment).forEach(tu -> {
            tu.setReplenishment(null);
            transportUnitRepository.save(tu);
            log.info("Successfully released Transport Unit {} from canceled replenishment {}",
                tu.getBarcode(), replenishmentId);
        });

        replenishment.getTask().ifPresent(task -> {
            List<Allocation> allocations = allocationRepository.findAllByTaskId(task.getId());
            for (Allocation allocation : allocations) {
                if (allocation.getStatus() != Status.COMPLETED) {
                    Stock stock = allocation.getStock();
                    stock.setReservedQuantity(stock.getReservedQuantity() - allocation.getQuantity());
                    stockRepository.save(stock);
                    allocation.setStatus(Status.CANCELED);
                }
            }
            allocationRepository.saveAll(allocations);
        });

        replenishment.setStatus(Status.CANCELED);
        return replenishmentMapper.toResponse(replenishmentRepository.save(replenishment));
    }

    public ReplenishmentResponse getReplenishmentById(Long replenishmentId) {
        return replenishmentMapper.toResponse(getReplenishment(replenishmentId));
    }

    public List<ReplenishmentResponse> getAllReplenishments() {
        return replenishmentRepository.findAll().stream()
            .map(replenishmentMapper::toResponse)
            .toList();
    }

    public List<ReplenishmentResponse> searchReplenishments(ReplenishmentSearchRequest request) {
        List<Replenishment> tasks = replenishmentRepository.filter(
            request.id(),
            request.taskId(),
            request.productId(),
            request.requestedQuantity(),
            request.status(),
            request.destinationLocationId()
        );

        return tasks.stream().map(replenishmentMapper::toResponse).toList();
    }

    public List<ShortageReplenishmentResponse> getShortageReplenishments() {
        return replenishmentRepository.findAllByCreatedByUsername(securityFacade.getCurrentUsername()).stream()
            .filter(this::isShortageReplenishment)
            .map(this::toShortageReplenishmentResponse)
            .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
            .toList();
    }

    public ShortageReplenishmentDetailsResponse getShortageDetails(Long replenishmentId) {
        Replenishment replenishment = getReplenishment(replenishmentId);
        List<Allocation> allocations = replenishment.getTask()
            .map(task -> allocationRepository.findAllByTaskId(task.getId()))
            .orElse(List.of());

        List<AffectedReplenishmentLineResponse> shortageLines = isShortageReplenishment(replenishment)
            ? List.of(toAffectedReplenishmentLineResponse(replenishment, allocations))
            : List.of();

        return new ShortageReplenishmentDetailsResponse(
            replenishment.getId(),
            replenishment.getTask().map(Task::getId).orElse(null),
            replenishment.getDestinationLocation().getId(),
            replenishment.getDestinationLocation().getBarcode(),
            replenishment.getStatus().name(),
            shortageLines
        );
    }

    private boolean isShortageReplenishment(Replenishment replenishment) {
        List<Allocation> allocations = replenishment.getTask()
            .map(task -> allocationRepository.findAllByTaskId(task.getId()))
            .orElse(List.of());

        boolean hasShortage = allocations.stream().anyMatch(allocation ->
            allocation.getStatus() == Status.PARTIALLY_COMPLETED
                || allocation.getStatus() == Status.SHORTAGE
                || allocation.getStatus() == Status.CANCELED
                || resolvedShortageQuantity(allocation) > 0
        );

        boolean requiresRevalidation = replenishment.getTask()
            .map(Task::getStatus)
            .filter(TaskStatus.REQUIRES_REVALIDATION::equals)
            .isPresent();

        return hasShortage
            || requiresRevalidation
            || replenishment.getStatus() == Status.PARTIALLY_COMPLETED
            || replenishment.getStatus() == Status.SHORTAGE
            || replenishment.getStatus() == Status.CANCELED;
    }

    private ShortageReplenishmentResponse toShortageReplenishmentResponse(Replenishment replenishment) {
        return new ShortageReplenishmentResponse(
            replenishment.getId(),
            replenishment.getTask().map(Task::getId).orElse(null),
            replenishment.getDestinationLocation().getBarcode(),
            replenishment.getStatus().name(),
            1,
            1,
            replenishment.getCreatedAt(),
            replenishment.getUpdatedAt()
        );
    }

    private AffectedReplenishmentLineResponse toAffectedReplenishmentLineResponse(Replenishment replenishment, List<Allocation> allocations) {
        List<Allocation> sortedAllocations = allocations.stream()
            .sorted(Comparator.comparing(Allocation::getCreatedAt).thenComparing(Allocation::getId))
            .toList();

        int deliveredQuantity = sortedAllocations.stream()
            .filter(allocation -> allocation.getStatus() != Status.CANCELED)
            .mapToInt(allocation -> allocation.getPickedQuantity().orElse(0))
            .sum();
        int requestedQuantity = Optional.of(replenishment.getRequestedQuantity()).orElse(0);
        int shortageQuantity = Math.max(0, requestedQuantity - deliveredQuantity);

        Long originalLocationId = sortedAllocations.isEmpty() ? null : sortedAllocations.getFirst().getStock().getLocation().getId();
        String originalLocationBarcode = sortedAllocations.isEmpty() ? null : sortedAllocations.getFirst().getStock().getLocation().getBarcode();
        boolean revalidationRequired = replenishment.getTask()
            .map(Task::getStatus)
            .filter(TaskStatus.REQUIRES_REVALIDATION::equals)
            .isPresent();

        Product product = replenishment.getProduct();
        Location destinationLocation = replenishment.getDestinationLocation();

        return new AffectedReplenishmentLineResponse(
            replenishment.getId(),
            replenishment.getTask().map(Task::getId).orElse(null),
            product.getId(),
            product.getName(),
            requestedQuantity,
            deliveredQuantity,
            shortageQuantity,
            originalLocationId,
            originalLocationBarcode,
            destinationLocation.getId(),
            destinationLocation.getBarcode(),
            replenishment.getStatus().name(),
            revalidationRequired,
            replenishment.getCreatedAt(),
            replenishment.getUpdatedAt()
        );
    }

    private int resolvedShortageQuantity(Allocation allocation) {
        if (allocation.getPickedQuantity().isEmpty()) {
            return 0;
        }
        Integer pickedQuantity = allocation.getPickedQuantity().orElse(0);
        return Math.max(0, Optional.ofNullable(allocation.getQuantity()).orElse(0) - pickedQuantity);
    }

    private Replenishment getReplenishment(Long replenishmentId) {
        return replenishmentRepository.findById(replenishmentId)
            .orElseThrow(() -> new ReplenishmentNotFoundException(replenishmentId));
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Location getLocation(Long locationId) {
        return locationRepository.findById(locationId)
            .orElseThrow(() -> new LocationNotFoundException(locationId));
    }

    /**
     * Assigns a replenishment to an operator by creating a task and linking it.
     *
     * @param replenishmentId the ID of the replenishment
     * @param operatorId      the ID of the operator
     */
    @Transactional
    public void assignReplenishment(Long replenishmentId, Long operatorId) {
        Replenishment replenishment = getReplenishment(replenishmentId);
        if (replenishment.getStatus() != Status.CREATED) {
            throw new InvalidRequestException("Replenishment assignment is only allowed for CREATED replenishments.");
        }
        Task task = taskService.createTask(TaskType.REPLENISHMENT, replenishment.getRequestedQuantity(),
            replenishment.getProduct().getId());
        replenishment.setTask(task);
        replenishmentRepository.saveAndFlush(replenishment);
        taskService.assignTask(task.getId(), operatorId);
    }

    @Transactional
    public void importReplenishmentsFromFile(MultipartFile file) {
        List<ReplenishmentCreateRequest> replenishments = importService.importData(file, ReplenishmentInfo.class);
        replenishments.forEach(this::createReplenishment);
    }
}
