package com.isd.wms.service;

import com.isd.wms.dto.allocation.AllocationCompletionResponse;
import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.entity.*;
import com.isd.wms.enums.*;
import com.isd.wms.mapper.OperatorSummaryMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.allocation.PickingOperatorStrategy;
import com.isd.wms.service.allocation.ShortageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class Def21PickingAuditRemediationTest {

    private InventoryHistoryRepository historyRepository;
    private InventoryService inventoryService;
    private ReplenishmentService replenishmentService;
    private StockRepository stockRepository;

    private List<InventoryHistory> savedHistory;
    private AtomicInteger replenishmentCheckStockQty;

    private Product product;
    private Location location;
    private User operator;

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        savedHistory = new ArrayList<>();
        replenishmentCheckStockQty = new AtomicInteger(-1);

        product = new Product();
        ReflectionTestUtils.setField(product, "id", 10L);
        product.setName("Test Product");
        product.setBarcode("PROD-001");
        product.setMinThreshold(50);

        location = new Location();
        ReflectionTestUtils.setField(location, "id", 20L);
        location.setBarcode("PICK-01");
        location.setZone(Zone.PICKING);
        location.setIsActive(true);
        location.setAvailable(true);

        operator = new User("picker_dan", "dan@isd.com", "pass", Role.ROLE_OPERATOR, true, null, null);
        ReflectionTestUtils.setField(operator, "id", 99L);

        historyRepository = createProxy(InventoryHistoryRepository.class, (proxy, method, args) -> {
            if ("save".equals(method.getName())) {
                InventoryHistory h = (InventoryHistory) args[0];
                savedHistory.add(h);
                return h;
            }
            return null;
        });

        stockRepository = createProxy(StockRepository.class, (proxy, method, args) -> {
            if ("save".equals(method.getName())) {
                return args[0];
            }
            return null;
        });

        replenishmentService = new ReplenishmentService(null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public void checkAndTriggerAutoReplenishment(Product p, Location l, int qty) {
                replenishmentCheckStockQty.set(qty);
            }
        };

        inventoryService = new InventoryService(
            stockRepository,
            historyRepository,
            null, // productRepository
            null, // locationRepository
            null, // userRepository
            null, // stockMapper
            null, // inventoryHistoryMapper
            replenishmentService, // replenishmentService
            null, // importService
            null, // inventoryAdjustmentService
            null  // securityFacade
        );
    }

    @Test
    @DisplayName("Audit continuity: recordPickingHistory logs exact post-deduction stock and mathematically consistent previous quantity")
    void recordPickingHistory_capturesAccurateAuditMath() {
        Stock stock = new Stock(product, location);
        ReflectionTestUtils.setField(stock, "id", 101L);
        stock.setQuantity(85); // Stock has already been decremented by 15 from 100
        stock.setReservedQuantity(0);

        inventoryService.recordPickingHistory(stock, 15, operator);

        assertThat(savedHistory).hasSize(1);
        InventoryHistory history = savedHistory.get(0);

        assertThat(history.getAlteredQuantity()).isEqualTo(-15);
        assertThat(history.getQuantityAfterChange()).isEqualTo(85);
        assertThat(history.getPreviousQuantity()).isEqualTo(100);
        // Mathematical identity: previousQuantity + alteredQuantity == quantityAfterChange
        assertThat(history.getPreviousQuantity() + history.getAlteredQuantity())
            .isEqualTo(history.getQuantityAfterChange());
    }

    @Test
    @DisplayName("Replenishment trigger: triggerReplenishmentCheck inspects the decremented stock quantity, not initial stock")
    void triggerReplenishmentCheck_runsOnPostPickStock() {
        Stock stock = new Stock(product, location);
        ReflectionTestUtils.setField(stock, "id", 101L);
        stock.setQuantity(40); // Initial 60, decremented by 20 to 40 (below threshold 50)
        stock.setReservedQuantity(0);

        inventoryService.recordPickingHistory(stock, 20, operator);

        assertThat(replenishmentCheckStockQty.get()).isEqualTo(40);
    }

    @Test
    @DisplayName("PickingOperatorStrategy: completes allocation and logs audit after deduction with mathematical continuity")
    void pickingOperatorStrategy_executesAuditAfterStockDeduction() {
        Stock stock = new Stock(product, location);
        ReflectionTestUtils.setField(stock, "id", 501L);
        stock.setQuantity(100);
        stock.setReservedQuantity(20);

        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", 301L);
        task.setTaskType(TaskType.PICKING_ORDER);

        Allocation allocation = new Allocation();
        ReflectionTestUtils.setField(allocation, "id", 201L);
        allocation.setStock(stock);
        allocation.setTask(task);
        allocation.setQuantity(20);
        allocation.setPickedQuantity(20);

        Order order = new Order("ORD-001");
        ReflectionTestUtils.setField(order, "id", 1L);
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setDestinationLocation(location);

        OrderLine orderLine = new OrderLine(order, product, 20);
        ReflectionTestUtils.setField(orderLine, "id", 10L);
        orderLine.setTask(task);

        OrderLineRepository orderLineRepository = createProxy(OrderLineRepository.class, (proxy, method, args) -> {
            if ("findByTaskIdWithLock".equals(method.getName()) || "findByTaskId".equals(method.getName())) {
                return Optional.of(orderLine);
            }
            if ("findAllByOrderId".equals(method.getName())) {
                return List.of(orderLine);
            }
            if ("save".equals(method.getName())) {
                return args[0];
            }
            return null;
        });

        AllocationRepository allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> {
            if ("findAllByOrder".equals(method.getName())) {
                return List.of(allocation);
            }
            return Collections.emptyList();
        });

        WorkflowService workflowService = new WorkflowService(null, null, null, null, null) {
            @Override
            public AllocationCompletionResult executeAllocationCompletion(Allocation alloc) {
                // Simulate stock deduction in workflowService
                stock.setQuantity(stock.getQuantity() - 20); // 100 - 20 = 80
                stock.setReservedQuantity(stock.getReservedQuantity() - 20); // 20 - 20 = 0
                return new AllocationCompletionResult(AllocationCompletionStatus.COMPLETED, TaskType.PICKING_ORDER, 201L);
            }
        };

        PickingFlowService pickingFlowService = new PickingFlowService();

        OperatorSummaryMapper summaryMapper = new OperatorSummaryMapper();
        ShortageResolver shortageResolver = new ShortageResolver(null, null) {
            @Override
            public List<Allocation> resolveShortage(Allocation sourceAllocation, int shortageQuantity, String taskContext) {
                return Collections.emptyList();
            }
        };
        TransportUnitRepository tuRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> {
            if (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class)) {
                return Boolean.FALSE;
            }
            if (method.getReturnType().equals(Optional.class)) {
                return Optional.empty();
            }
            if (method.getReturnType().equals(List.class)) {
                return Collections.emptyList();
            }
            return null;
        });
        OrderRepository orderRepository = createProxy(OrderRepository.class, (proxy, method, args) -> null);

        PickingOperatorStrategy strategy = new PickingOperatorStrategy(
            allocationRepository,
            orderLineRepository,
            orderRepository,
            tuRepository,
            stockRepository,
            inventoryService,
            workflowService,
            shortageResolver,
            pickingFlowService,
            summaryMapper
        );

        AllocationCompletionResponse response = strategy.complete(allocation, 20, operator);

        assertThat(response.status()).isEqualTo(AllocationCompletionStatus.COMPLETED);
        assertThat(stock.getQuantity()).isEqualTo(80);

        // Verify audit history was captured after deduction
        assertThat(savedHistory).hasSize(1);
        InventoryHistory history = savedHistory.get(0);
        assertThat(history.getAlteredQuantity()).isEqualTo(-20);
        assertThat(history.getQuantityAfterChange()).isEqualTo(80);
        assertThat(history.getPreviousQuantity()).isEqualTo(100);
        assertThat(history.getPreviousQuantity() + history.getAlteredQuantity())
            .isEqualTo(history.getQuantityAfterChange());
    }
}
