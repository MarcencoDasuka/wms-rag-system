package com.isd.wms.service;

import com.isd.wms.dto.inventory.*;
import com.isd.wms.entity.*;
import com.isd.wms.enums.InventoryAdjustmentReason;
import com.isd.wms.enums.InventoryOperationType;
import com.isd.wms.enums.Zone;
import com.isd.wms.exception.*;
import com.isd.wms.mapper.InventoryHistoryMapper;
import com.isd.wms.mapper.StockMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.imports.dto.StockInfo;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing inventory (stock) operations.
 * <p>
 * Provides CRUD operations for stock records, including adding, removing,
 * and adjusting quantities. It also records inventory history for each
 * operation and triggers replenishment checks when stock levels drop.
 * </p>
 * <p>
 * Import functionality is supported via {@link ImportService} for bulk
 * stock additions from CSV/Excel files.
 * </p>
 *
 * @see Stock
 * @see InventoryHistory
 * @see ReplenishmentService
 * @see InventoryAdjustmentService
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final StockRepository stockRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final StockMapper stockMapper;
    private final InventoryHistoryMapper inventoryHistoryMapper;
    private final ReplenishmentService replenishmentService;
    private final ImportService importService;
    private final InventoryAdjustmentService inventoryAdjustmentService;
    private final SecurityFacade securityFacade;

    public List<StockResponse> getAllStock() {
        return stockRepository.findAllByAvailableIsTrue().stream()
            .map(stockMapper::toResponse)
            .toList();
    }

    public StockResponse getStockById(Long stockId) {
        return stockMapper.toResponse(getStock(stockId));
    }

    /**
     * Adds stock to a location. If the location already contains stock of a
     * different product, the operation is rejected unless the location is empty.
     *
     * @param request the add stock request
     * @return the updated stock response
     * @throws InvalidRequestException if the location is occupied by another product
     */
    @Transactional
    public StockResponse addStock(AddStockRequest request) {
        InventoryService.validateStockDate(request.manufactureDate(), request.expirationDate());
        log.info("Adding stock: productId={}, locationId={}, quantity={}, userId={}",
            request.productId(), request.locationId(), request.quantity(), request.userId());

        Product product = getProduct(request.productId());
        Location location = getLocation(request.locationId());
        User user = getUser(request.userId());

        if(stockRepository.existsByLocationAndAvailableIsTrueAndProductIsNot(location, product)) {
            throw new InvalidRequestException("Location is already occupied by a different product with remaining quantity.");
        }

        Stock stock = stockRepository.findByProductIdAndLocationId(product.getId(), location.getId())
            .orElseGet(() -> new Stock(product, location));

        int quantity = request.quantity() == null ? 0 : request.quantity();
        int reservedQuantity = request.reservedQuantity() == null ? 0 : request.reservedQuantity();

        stock.setQuantity(stock.getQuantity() + quantity);
        stock.setReservedQuantity(stock.getReservedQuantity() + reservedQuantity);
        stock.setManufactureDate(request.manufactureDate());
        stock.setExpirationDate(request.expirationDate());
        stock.setAvailable(true);

        Stock savedStock = stockRepository.save(stock);

        createHistory(savedStock, quantity, savedStock.getQuantity(), null, location,
            InventoryOperationType.ADD_STOCK, null, null, user);

        log.info("Stock added successfully: stockId={}, finalQuantity={}", savedStock.getId(), savedStock.getQuantity());
        return stockMapper.toResponse(savedStock);
    }

    @Transactional
    public void deleteStock(Long stockId) {
        inventoryAdjustmentService.adjustStock(
            stockId,
            new InventoryAdjustmentRequest(
                0,
                securityFacade.getCurrentUser().getId(),
                InventoryAdjustmentReason.INVENTORY_MISMATCH,
                "Deleted manually",
                null,
                null
            )
        );
    }

    /**
     * Removes a specified quantity of unreserved stock from a location.
     *
     * @param request the remove stock request
     * @return the updated stock response
     * @throws InsufficientStockException if the available quantity is insufficient
     */
    @Transactional
    public StockResponse removeStock(RemoveStockRequest request) {
        log.info("Removing stock: stockId={}, quantity={}, userId={}",
            request.getStockId(), request.getQuantity(), request.getUserId());

        Stock stock = getStock(request.getStockId());
        User user = getUser(request.getUserId());

        int availableQuantity = stock.getQuantity() - stock.getReservedQuantity();

        if (request.getQuantity() > availableQuantity) {
            log.warn("Insufficient unreserved stock: stockId={}, requestedQuantity={}, " +
                    "availableQuantity={}, reservedQuantity={}, userId={}", stock.getId(), request.getQuantity(),
                availableQuantity, stock.getReservedQuantity(), request.getUserId());
            throw new InsufficientStockException(stock.getId(), request.getQuantity(), availableQuantity);
        }

        int finalQuantity = stock.getQuantity() - request.getQuantity();
        stock.setQuantity(finalQuantity);
        if (finalQuantity == 0) {
            stock.setAvailable(false);
        }
        stockRepository.save(stock);

        createHistory(stock, -request.getQuantity(), finalQuantity, stock.getLocation(), null,
            InventoryOperationType.REMOVE_STOCK, null, null, user);

        triggerReplenishmentCheck(stock);

        log.info("Stock removed successfully: stockId={}, finalQuantity={}", stock.getId(), finalQuantity);
        return stockMapper.toResponse(stock);
    }

    public List<InventoryHistoryResponse> getAllHistory() {
        return inventoryHistoryRepository.findAll().stream()
            .map(inventoryHistoryMapper::toResponse)
            .toList();
    }

    public List<InventoryHistoryResponse> getHistoryForStock(Long stockId) {
        Stock stock = getStock(stockId);
        Long productId = stock.getProduct().map(Product::getId).orElse(null);
        Long locationId = stock.getLocation().getId();
        return inventoryHistoryRepository
            .findByProductIdAndSourceLocationIdOrProductIdAndDestinationLocationId(
                productId, locationId, productId, locationId)
            .stream()
            .map(inventoryHistoryMapper::toResponse)
            .toList();
    }

    @Transactional
    public void recordPickingHistory(Stock stock, Integer pickedQuantity, User user) {
        recordPickingHistory(stock, pickedQuantity, user, null, null);
    }

    @Transactional
    public void recordPickingHistory(
        Stock stock,
        Integer pickedQuantity,
        User user,
        InventoryAdjustmentReason adjustmentReason,
        String comment
    ) {
        createHistory(stock, -pickedQuantity, stock.getQuantity(), stock.getLocation(), null,
            InventoryOperationType.PICKING, adjustmentReason, comment, user);
        triggerReplenishmentCheck(stock);
    }

    /**
     * Universally records a shortage adjustment for any task type (Picking or Replenishment).
     */
    @Transactional
    public void recordShortageAdjustment(
        Stock stock,
        Integer shortageQuantity,
        User user,
        InventoryOperationType operationType,
        String comment
    ) {
        if (shortageQuantity == null || shortageQuantity <= 0) {
            return;
        }
        int quantityAfterChange = Math.max(0, stock.getQuantity() - shortageQuantity);

        stock.setQuantity(quantityAfterChange);
        stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - shortageQuantity));

        if (stock.getQuantity() == 0 && stock.getReservedQuantity() == 0) {
            stock.setAvailable(false);
        }
        stockRepository.save(stock);

        InventoryAdjustmentReason reason = (operationType == InventoryOperationType.PICKING_SHORTAGE)
            ? InventoryAdjustmentReason.PICKING_SHORTAGE : null;

        createHistory(stock, -shortageQuantity, quantityAfterChange, stock.getLocation(), null,
            operationType, reason, comment, user);

        log.info("Shortage adjustment recorded: stockId={}, operationType={}, shortageQuantity={}, quantityAfterChange={}",
            stock.getId(), operationType.name(), shortageQuantity, quantityAfterChange);

        triggerReplenishmentCheck(stock);
    }

    private void createHistory(
        Stock stock,
        Integer alteredQuantity,
        Integer quantityAfterChange,
        Location sourceLocation,
        Location destinationLocation,
        InventoryOperationType operationType,
        InventoryAdjustmentReason adjustmentReason,
        String comment,
        User user
    ) {
        Product product = stock.getProduct().orElse(null);
        InventoryHistory history = new InventoryHistory(
            product,
            product == null ? null : product.getBarcode(),
            alteredQuantity,
            quantityAfterChange,
            stock.getQuantity() - alteredQuantity,
            sourceLocation,
            destinationLocation,
            operationType,
            adjustmentReason,
            comment,
            user
        );
        history.setTimestamp(LocalDateTime.now());
        inventoryHistoryRepository.save(history);
    }

    private void triggerReplenishmentCheck(Stock stock) {
        Product product = stock.getProduct().orElse(null);
        if (product == null) return;

        if (stock.getLocation().getZone() != Zone.PICKING) {
            return;
        }
        int locationQty = stock.getQuantity() - stock.getReservedQuantity();

        replenishmentService.checkAndTriggerAutoReplenishment(product, stock.getLocation(), locationQty);
    }

    private Stock getStock(Long stockId) {
        return stockRepository.findById(stockId)
            .orElseThrow(() -> {
                log.warn("Stock not found: stockId={}", stockId);
                return new StockNotFoundException(stockId);
            });
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> {
                log.warn("Product not found: productId={}", productId);
                return new ProductNotFoundException(productId);
            });
    }

    private Location getLocation(Long locationId) {
        return locationRepository.findById(locationId)
            .orElseThrow(() -> {
                log.warn("Location not found: locationId={}", locationId);
                return new LocationNotFoundException(locationId);
            });
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> {
                log.warn("User not found: userId={}", userId);
                return new UserNotFoundException(userId);
            });
    }

    static void validateStockDate(LocalDate manufactureDate, LocalDate expirationDate) {
        LocalDate today = LocalDate.now();

        if (manufactureDate != null && manufactureDate.isAfter(today)) {
            throw new InvalidRequestException(
                "Manufacture Date must be before today.");
        }

        if (expirationDate != null && expirationDate.isBefore(today)) {
            throw new InvalidRequestException(
                "Expiration Date must be after today.");
        }
    }

    /**
     * Imports stock records from an uploaded file.
     *
     * @param file the multipart file containing stock data
     */
    @Transactional
    public void importStocksFromFile(MultipartFile file) {
        List<AddStockRequest> stocks = importService.importData(file, StockInfo.class);
        stocks.forEach(this::addStock);
    }
}
