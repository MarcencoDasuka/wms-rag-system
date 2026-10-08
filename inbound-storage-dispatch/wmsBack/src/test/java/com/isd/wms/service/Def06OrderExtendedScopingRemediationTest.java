package com.isd.wms.service;

import com.isd.wms.dto.order.ExtendedOrderResponse;
import com.isd.wms.dto.order.OrderResponse;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.User;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.mapper.ExtendedOrderMapper;
import com.isd.wms.mapper.OrderLineMapper;
import com.isd.wms.mapper.OrderMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class Def06OrderExtendedScopingRemediationTest {

    private OrderRepository orderRepository;
    private OrderService orderService;

    private String currentUsername;
    private boolean isDevUser;
    private User currentUser;

    private Order aliceOrder;
    private Order bobOrder;

    private boolean findAllCalled;
    private String accessibleBySupervisorArgument;

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        currentUsername = "supervisor_alice";
        isDevUser = false;
        currentUser = new User("supervisor_alice", "alice@isd.com", "pass", Role.ROLE_SUPERVISOR, true, null, null);
        ReflectionTestUtils.setField(currentUser, "id", 101L);

        Location loc = new Location();
        ReflectionTestUtils.setField(loc, "id", 10L);
        loc.setBarcode("LOC-01");

        aliceOrder = new Order("LOGIC-ALICE");
        ReflectionTestUtils.setField(aliceOrder, "id", 1L);
        aliceOrder.setCreatedBy("supervisor_alice");
        aliceOrder.setStatus(OrderStatus.CREATED);
        aliceOrder.setDestinationLocation(loc);
        aliceOrder.setOrderLines(Collections.emptyList());

        bobOrder = new Order("LOGIC-BOB");
        ReflectionTestUtils.setField(bobOrder, "id", 2L);
        bobOrder.setCreatedBy("supervisor_bob");
        bobOrder.setStatus(OrderStatus.CREATED);
        bobOrder.setDestinationLocation(loc);
        bobOrder.setOrderLines(Collections.emptyList());

        findAllCalled = false;
        accessibleBySupervisorArgument = null;

        orderRepository = createProxy(OrderRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findAll".equals(name)) {
                findAllCalled = true;
                return List.of(aliceOrder, bobOrder);
            }
            if ("findAllAccessibleBySupervisor".equals(name)) {
                accessibleBySupervisorArgument = (String) args[0];
                if ("supervisor_alice".equalsIgnoreCase(accessibleBySupervisorArgument)) {
                    return List.of(aliceOrder);
                }
                return Collections.emptyList();
            }
            if ("findOperatorIdsByOrderIds".equals(name)) {
                return Collections.emptyList();
            }
            return null;
        });

        TransportUnitRepository transportUnitRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> {
            if ("findByOrder".equals(method.getName())) {
                return Optional.empty();
            }
            if ("findAllByOrderIds".equals(method.getName())) {
                return Collections.emptyList();
            }
            return Collections.emptyList();
        });

        OrderMapper orderMapper = new OrderMapper(transportUnitRepository);
        ExtendedOrderMapper extendedOrderMapper = new ExtendedOrderMapper(orderMapper, new OrderLineMapper());

        LocationRepository locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> null);
        AllocationRepository allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> Collections.emptyList());
        TaskRepository taskRepository = createProxy(TaskRepository.class, (proxy, method, args) -> Collections.emptyList());
        OrderLineRepository orderLineRepository = createProxy(OrderLineRepository.class, (proxy, method, args) -> Collections.emptyList());

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public boolean hasRole(Role roleName) {
                if (roleName == Role.ROLE_DEV) {
                    return isDevUser;
                }
                return false;
            }

            @Override
            public String getCurrentUsername() {
                return currentUsername;
            }

            @Override
            public User getCurrentUser() {
                return currentUser;
            }
        };

        orderService = new OrderService(
                extendedOrderMapper,
                orderMapper,
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
    @DisplayName("DEF-06: getAllOrders for supervisor must query only accessible orders (createdBy OR task supervisor)")
    void getAllOrders_forSupervisor_mustScopeToAccessibleOrders() {
        currentUsername = "supervisor_alice";
        isDevUser = false;

        List<OrderResponse> results = orderService.getAllOrders();

        assertThat(findAllCalled).isFalse();
        assertThat(accessibleBySupervisorArgument).isEqualTo("supervisor_alice");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(1L);
        assertThat(results.get(0).logicId()).isEqualTo("LOGIC-ALICE");
    }

    @Test
    @DisplayName("DEF-06: getAllOrders for ROLE_DEV must return all orders across all supervisors")
    void getAllOrders_forDev_mustReturnAllOrders() {
        currentUsername = "developer_dave";
        isDevUser = true;

        List<OrderResponse> results = orderService.getAllOrders();

        assertThat(findAllCalled).isTrue();
        assertThat(accessibleBySupervisorArgument).isNull();
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("DEF-06: getAllExtendedOrders for supervisor must scope to accessible orders, preventing data leak")
    void getAllExtendedOrders_forSupervisor_mustScopeToAccessibleOrders() {
        currentUsername = "supervisor_alice";
        isDevUser = false;

        List<ExtendedOrderResponse> results = orderService.getAllExtendedOrders();

        assertThat(findAllCalled).isFalse();
        assertThat(accessibleBySupervisorArgument).isEqualTo("supervisor_alice");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).order().id()).isEqualTo(1L);
    }
}
