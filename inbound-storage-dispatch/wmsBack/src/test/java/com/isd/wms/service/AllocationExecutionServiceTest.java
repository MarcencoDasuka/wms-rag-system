package com.isd.wms.service;

import com.isd.wms.dto.allocation.AllocationCompletionResponse;
import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.dto.allocation.ConfirmPickedQuantityRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.*;
import com.isd.wms.repository.*;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllocationExecutionServiceTest {

    @Mock private AllocationRepository allocationRepository;
    @Mock private StockRepository stockRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TransportUnitRepository tuRepository;
    @Mock private UserRepository userRepository;
    @Mock private InventoryService inventoryService;
    @Mock private SecurityFacade securityFacade;
    @Mock private WorkflowService workflowService;
    @Mock private ReplenishmentRepository replenishmentRepository;
    @Spy private PickingFlowService pickingFlowService;

    @InjectMocks
    private AllocationExecutionService allocationExecutionService;

    private User operator;
    private Allocation allocation;
    private Stock stock;
    private Task task;
    private OrderLine orderLine;
    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
        operator = new User("operator", "op@test.com", "pass", Role.ROLE_OPERATOR, true, null, null);
        ReflectionTestUtils.setField(operator, "id", 1L);

        product = new Product("Coca-Cola", "SKU-001", null, null);
        ReflectionTestUtils.setField(product, "id", 10L);

        Location location = new Location("name", "PICK-01", Zone.PICKING, null, true);
        ReflectionTestUtils.setField(location, "id", 20L);

        stock = new Stock(product, location, 50, 10, null, null);
        ReflectionTestUtils.setField(stock, "id", 30L);

        task = new Task(null, TaskType.PICKING_ORDER, 10);
        task.setOperator(operator);
        ReflectionTestUtils.setField(task, "id", 40L);
        task.setStatus(TaskStatus.IN_PROGRESS);

        allocation = new Allocation(task, stock, 10, Status.ASSIGNED);
        ReflectionTestUtils.setField(allocation, "id", 50L);
        ReflectionTestUtils.setField(allocation, "createdAt", LocalDateTime.now());

        order = new Order("ORDER-001");
        ReflectionTestUtils.setField(order, "id", 60L);
        order.setStatus(OrderStatus.IN_PROGRESS);

        Location dispatchLocation = new Location("Dispatch", "DISP-01", Zone.DISPATCH, null, true);
        ReflectionTestUtils.setField(dispatchLocation, "id", 999L);
        order.setDestinationLocation(dispatchLocation);

        orderLine = new OrderLine(order, task, product, 10);
        ReflectionTestUtils.setField(orderLine, "id", 70L);
        ReflectionTestUtils.setField(order, "orderLines", List.of(orderLine));

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("operator");
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername("operator")).thenReturn(Optional.of(operator));
        lenient().when(securityFacade.getCurrentUsername()).thenReturn("operator");
        lenient().when(securityFacade.getCurrentUser()).thenReturn(operator);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void confirmPickedQuantitySuccessfully() {
        allocation.setStatus(Status.IN_PROGRESS);
        allocation.setProductScanned(true);
        when(allocationRepository.findById(50L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.save(allocation)).thenReturn(allocation);

        allocationExecutionService.confirmPickedQuantity(50L, new ConfirmPickedQuantityRequest(10));

        assertThat(allocation.getPickedQuantity()).isEqualTo(10);
    }

    @Test
    void completeAllocationSuccessfully_NoShortage() {
        prepareAllocationForCompletion(10);
        when(allocationRepository.findById(50L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.save(allocation)).thenReturn(allocation);
        when(orderLineRepository.findByTaskId(40L)).thenReturn(Optional.of(orderLine));
        when(workflowService.executeAllocationCompletion(allocation)).thenReturn(new AllocationCompletionResult(AllocationCompletionStatus.COMPLETED, TaskType.PICKING_ORDER, 50L));

        AllocationCompletionResponse response = allocationExecutionService.completeAllocation(50L);

        assertThat(response.status()).isEqualTo(AllocationCompletionStatus.COMPLETED);
        assertThat(response.shortageQuantity()).isEqualTo(0);
        assertThat(response.newProcessCreated()).isFalse();
        verify(inventoryService).recordPickingHistory(eq(stock), eq(10), eq(operator), isNull(), isNull());
    }

    @Test
    void completeAllocation_withShortage_createsAlternativeAllocations() {
        prepareAllocationForCompletion(7);
        when(allocationRepository.findById(50L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.save(allocation)).thenReturn(allocation);
        when(orderLineRepository.findByTaskId(40L)).thenReturn(Optional.of(orderLine));
        when(workflowService.executeAllocationCompletion(allocation)).thenReturn(new AllocationCompletionResult(AllocationCompletionStatus.COMPLETED, TaskType.PICKING_ORDER, 50L));

        Stock alternativeStock = new Stock(product, new Location("Alt", "PICK-02", Zone.PICKING, null, true), 100, 0, null, null);
        ReflectionTestUtils.setField(alternativeStock, "id", 99L);
        when(stockRepository.findAvailableStocksByProductIdAndZone(10L, Zone.PICKING)).thenReturn(List.of(alternativeStock));

        AllocationCompletionResponse response = allocationExecutionService.completeAllocation(50L);

        verify(inventoryService).recordShortageAdjustment(eq(stock), eq(3), eq(operator), eq(InventoryOperationType.PICKING_SHORTAGE), eq("Picking shortage"));

        verify(allocationRepository).saveAll(argThat(list -> {
            List<Allocation> savedList = (List<Allocation>) list;
            return savedList.size() == 1 && savedList.get(0).getQuantity() == 3 && savedList.get(0).getStock().getId().equals(99L);
        }));

        assertThat(response.shortageQuantity()).isEqualTo(3);
        assertThat(response.newProcessCreated()).isTrue();
    }

    private void prepareAllocationForCompletion(int pickedQty) {
        allocation.setStatus(Status.IN_PROGRESS);
        allocation.setSourceLocationScanned(true);
        allocation.setProductScanned(true);
        allocation.setPickedQuantity(pickedQty);
    }

    @Test
    void completeAllocation_replenishmentWithShortage_createsAlternativeAllocations() {
        ReflectionTestUtils.setField(task, "taskType", TaskType.REPLENISHMENT);
        allocation.setStatus(Status.IN_PROGRESS);
        allocation.setSourceLocationScanned(true);
        allocation.setProductScanned(true);
        allocation.setPickedQuantity(7);

        Stock alternativeStock = new Stock(product, new Location("Alt", "REPL-02", Zone.PICKING, null, true), 100, 0, null, null);
        ReflectionTestUtils.setField(alternativeStock, "id", 99L);

        Replenishment dummyReplenishment = new Replenishment(product, 10, new Location());
        ReflectionTestUtils.setField(dummyReplenishment, "id", 777L);
        when(replenishmentRepository.findByTaskId(40L)).thenReturn(Optional.of(dummyReplenishment));

        when(allocationRepository.findById(50L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.save(allocation)).thenReturn(allocation);
        when(workflowService.executeAllocationCompletion(allocation)).thenReturn(new AllocationCompletionResult(AllocationCompletionStatus.COMPLETED, TaskType.REPLENISHMENT, 50L));

        when(stockRepository.findAvailableStocksByProductIdAndZone(10L, Zone.PICKING)).thenReturn(List.of(alternativeStock));

        AllocationCompletionResponse response = allocationExecutionService.completeAllocation(50L);

        verify(allocationRepository).saveAll(argThat(list -> {
            List<Allocation> savedList = (List<Allocation>) list;
            return savedList.size() == 1 && savedList.get(0).getQuantity() == 3 && savedList.get(0).getStock().getId().equals(99L);
        }));

        assertThat(response.shortageQuantity()).isEqualTo(3);
        assertThat(response.newProcessCreated()).isTrue();
    }
}
