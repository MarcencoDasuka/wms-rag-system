package com.isd.wms.service.allocation;

import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.OrderLine;
import com.isd.wms.entity.TransportUnit;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.OrderLineRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.TransportUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PickingOperatorStrategyCompletionTest {

    private AllocationRepository allocationRepository;
    private OrderLineRepository orderLineRepository;
    private OrderRepository orderRepository;
    private TransportUnitRepository tuRepository;

    private PickingOperatorStrategy strategy;

    private Order order;
    private Allocation allocation1;
    private Allocation allocation2;
    private OrderLine line1;
    private OrderLine line2;
    private TransportUnit tu;

    private List<Allocation> allocationsToReturn = new ArrayList<>();
    private List<OrderLine> linesToReturn = new ArrayList<>();
    private TransportUnit tuToReturn = null;
    private final AtomicBoolean orderSaved = new AtomicBoolean(false);
    private final AtomicBoolean tuSaved = new AtomicBoolean(false);

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        orderSaved.set(false);
        tuSaved.set(false);

        order = new Order();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.setStatus(OrderStatus.IN_PROGRESS);

        allocation1 = new Allocation();
        ReflectionTestUtils.setField(allocation1, "id", 1L);
        allocation1.setStatus(Status.COMPLETED);

        allocation2 = new Allocation();
        ReflectionTestUtils.setField(allocation2, "id", 2L);
        allocation2.setStatus(Status.COMPLETED);

        line1 = new OrderLine(order, null, null, 10);
        ReflectionTestUtils.setField(line1, "id", 10L);
        line1.setDeliveredQuantity(10);
        line1.setShortageQuantity(0);
        line1.setStatus(Status.COMPLETED);

        line2 = new OrderLine(order, null, null, 5);
        ReflectionTestUtils.setField(line2, "id", 20L);
        line2.setDeliveredQuantity(5);
        line2.setShortageQuantity(0);
        line2.setStatus(Status.COMPLETED);

        tu = new TransportUnit();
        tu.setBarcode("TU-001");
        tu.setOrder(order);

        allocationsToReturn = List.of(allocation1, allocation2);
        linesToReturn = List.of(line1, line2);
        tuToReturn = tu;

        allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> {
            if ("findAllByOrder".equals(method.getName())) {
                return allocationsToReturn;
            }
            return null;
        });

        orderLineRepository = createProxy(OrderLineRepository.class, (proxy, method, args) -> {
            if ("findAllByOrderId".equals(method.getName())) {
                return linesToReturn;
            }
            return null;
        });

        orderRepository = createProxy(OrderRepository.class, (proxy, method, args) -> {
            if ("save".equals(method.getName())) {
                orderSaved.set(true);
                return args[0];
            }
            return null;
        });

        tuRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> {
            if ("findByOrder".equals(method.getName())) {
                return Optional.ofNullable(tuToReturn);
            }
            if ("save".equals(method.getName())) {
                tuSaved.set(true);
                return args[0];
            }
            return null;
        });

        strategy = new PickingOperatorStrategy(
                allocationRepository, orderLineRepository, orderRepository, tuRepository,
                null, null, null, null, null, null
        );
    }

    @Test
    @DisplayName("PRE-FIX/POST-FIX: When 100% of order lines and allocations are COMPLETED, order status must be COMPLETED")
    void handleOrderCompletion_allCompleted_orderStatusMustBeCompleted() {
        linesToReturn = List.of(line1, line2);

        // Invoke handleOrderCompletion
        ReflectionTestUtils.invokeMethod(strategy, "handleOrderCompletion", order);

        assertThat(order.getStatus())
                .as("100% picked order must transition to COMPLETED, never PARTIALLY_COMPLETED")
                .isEqualTo(OrderStatus.COMPLETED);

        assertThat(orderSaved.get()).isTrue();
    }

    @Test
    @DisplayName("When all order lines are CANCELED, order status must be CANCELED and TU released")
    void handleOrderCompletion_allCanceled_orderStatusMustBeCanceledAndTuReleased() {
        allocation1.setStatus(Status.CANCELED);
        allocation2.setStatus(Status.CANCELED);
        line1.setStatus(Status.CANCELED);
        line2.setStatus(Status.CANCELED);

        ReflectionTestUtils.invokeMethod(strategy, "handleOrderCompletion", order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(tu.getOrder()).isNull();
        assertThat(tuSaved.get()).isTrue();
        assertThat(orderSaved.get()).isTrue();
    }

    @Test
    @DisplayName("When some lines are completed but others have shortage, order status must be PARTIALLY_COMPLETED")
    void handleOrderCompletion_partialShortage_orderStatusMustBePartiallyCompleted() {
        line2.setStatus(Status.SHORTAGE);
        line2.setShortageQuantity(3);
        line2.setDeliveredQuantity(2);

        ReflectionTestUtils.invokeMethod(strategy, "handleOrderCompletion", order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_COMPLETED);
        assertThat(orderSaved.get()).isTrue();
    }

    @Test
    @DisplayName("When one line is completed and another is canceled, order status must be PARTIALLY_COMPLETED")
    void handleOrderCompletion_completedAndCanceledMix_orderStatusMustBePartiallyCompleted() {
        allocation2.setStatus(Status.CANCELED);
        line2.setStatus(Status.CANCELED);

        ReflectionTestUtils.invokeMethod(strategy, "handleOrderCompletion", order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_COMPLETED);
        assertThat(orderSaved.get()).isTrue();
    }
}
