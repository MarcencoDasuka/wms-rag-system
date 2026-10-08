package com.isd.wms.service;

import com.isd.wms.entity.Order;
import com.isd.wms.entity.TransportUnit;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.*;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def12OrderDeletionLifecycleRemediationTest {

    private OrderRepository orderRepository;
    private TransportUnitRepository transportUnitRepository;

    private OrderService orderService;

    private Order testOrder;
    private TransportUnit linkedTu;
    private boolean orderDeleted;
    private boolean tuSaved;

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        orderDeleted = false;
        tuSaved = false;

        testOrder = new Order("LOGIC-ORD-DEL");
        ReflectionTestUtils.setField(testOrder, "id", 701L);
        testOrder.setCreatedBy("supervisor_alice");
        testOrder.setStatus(OrderStatus.CREATED);

        linkedTu = new TransportUnit();
        linkedTu.setBarcode("TU999999");
        linkedTu.setOrder(testOrder);

        orderRepository = createProxy(OrderRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findById".equals(name)) {
                return Optional.of(testOrder);
            }
            if ("findSupervisorUsernamesByOrder".equals(name)) {
                return Collections.emptyList();
            }
            if ("delete".equals(name)) {
                orderDeleted = true;
                return null;
            }
            return null;
        });

        OrderLineRepository orderLineRepository = createProxy(OrderLineRepository.class, (proxy, method, args) -> Collections.emptyList());

        transportUnitRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findByOrder".equals(name)) {
                return Optional.of(linkedTu);
            }
            if ("save".equals(name)) {
                tuSaved = true;
                return args[0];
            }
            return null;
        });

        LocationRepository locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> null);
        AllocationRepository allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> Collections.emptyList());
        TaskRepository taskRepository = createProxy(TaskRepository.class, (proxy, method, args) -> Collections.emptyList());

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role roleName) {
                return false;
            }

            @Override
            public String getCurrentUsername() {
                return "supervisor_alice";
            }
        };

        orderService = new OrderService(
                null,
                null,
                orderRepository,
                locationRepository,
                null,
                allocationRepository,
                taskRepository,
                orderLineRepository,
                null,
                securityFacade,
                null,
                transportUnitRepository
        );
    }

    @Test
    @DisplayName("DEF-12: deleteOrderById for CREATED status must succeed and release transport unit")
    void deleteOrderById_whenCreatedStatus_mustSucceedAndReleaseTu() {
        testOrder.setStatus(OrderStatus.CREATED);

        orderService.deleteOrderById(701L);

        assertThat(orderDeleted).isTrue();
        assertThat(tuSaved).isTrue();
        assertThat(linkedTu.getOrder()).isNull();
    }

    @Test
    @DisplayName("DEF-12: deleteOrderById for CANCELED status must succeed and release transport unit")
    void deleteOrderById_whenCanceledStatus_mustSucceedAndReleaseTu() {
        testOrder.setStatus(OrderStatus.CANCELED);

        orderService.deleteOrderById(701L);

        assertThat(orderDeleted).isTrue();
        assertThat(tuSaved).isTrue();
        assertThat(linkedTu.getOrder()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"ASSIGNED", "IN_PROGRESS", "PICKED", "COMPLETED", "PARTIALLY_COMPLETED"})
    @DisplayName("DEF-12: deleteOrderById for non-deletable lifecycle statuses must throw InvalidRequestException")
    void deleteOrderById_whenNonDeletableStatus_mustThrowInvalidRequestException(OrderStatus status) {
        testOrder.setStatus(status);

        assertThatThrownBy(() -> orderService.deleteOrderById(701L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot delete order with status: " + status)
                .hasMessageContaining("Only CREATED or CANCELED orders can be deleted");

        assertThat(orderDeleted).isFalse();
        assertThat(tuSaved).isFalse();
        assertThat(linkedTu.getOrder()).isEqualTo(testOrder);
    }
}
