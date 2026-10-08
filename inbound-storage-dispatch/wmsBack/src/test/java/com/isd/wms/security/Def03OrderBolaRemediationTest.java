package com.isd.wms.security;

import com.isd.wms.dto.order.OrderResponse;
import com.isd.wms.dto.order.OrderUpdateRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.OrderLine;
import com.isd.wms.entity.Task;
import com.isd.wms.entity.User;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.mapper.OrderMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def03OrderBolaRemediationTest {

    private OrderRepository orderRepository;
    private OrderService orderService;

    private String currentUsername;
    private boolean isDevUser;
    private User currentUser;

    private Order aliceOrder;
    private List<String> taskSupervisorUsernames;

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

        aliceOrder = new Order("LOGIC-ORD-001");
        ReflectionTestUtils.setField(aliceOrder, "id", 501L);
        aliceOrder.setCreatedBy("supervisor_alice");
        aliceOrder.setStatus(OrderStatus.CREATED);

        Location dest = new Location();
        ReflectionTestUtils.setField(dest, "id", 10L);
        dest.setBarcode("LOC-DEST-01");
        aliceOrder.setDestinationLocation(dest);

        taskSupervisorUsernames = new ArrayList<>();

        orderRepository = createProxy(OrderRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findById".equals(name)) {
                Long id = (Long) args[0];
                if (Long.valueOf(501L).equals(id)) {
                    return Optional.of(aliceOrder);
                }
                return Optional.empty();
            }
            if ("findSupervisorUsernamesByOrder".equals(name)) {
                return taskSupervisorUsernames;
            }
            if ("findOperatorIdByOrderId".equals(name)) {
                return Optional.empty();
            }
            if ("save".equals(name)) {
                return args[0];
            }
            if ("delete".equals(name)) {
                return null;
            }
            return null;
        });

        OrderLineRepository orderLineRepository = createProxy(OrderLineRepository.class, (proxy, method, args) -> {
            if ("findAllByOrderId".equals(method.getName())) {
                return Collections.emptyList();
            }
            return Collections.emptyList();
        });

        TransportUnitRepository transportUnitRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> {
            if ("findByOrder".equals(method.getName())) {
                return Optional.empty();
            }
            return Optional.empty();
        });

        LocationRepository locationRepository = createProxy(LocationRepository.class, (proxy, method, args) -> null);
        AllocationRepository allocationRepository = createProxy(AllocationRepository.class, (proxy, method, args) -> Collections.emptyList());
        TaskRepository taskRepository = createProxy(TaskRepository.class, (proxy, method, args) -> Collections.emptyList());

        OrderMapper orderMapper = new OrderMapper(transportUnitRepository);

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
                null,
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
    @DisplayName("DEF-03: Supervisor cannot access order created by another supervisor when not task supervisor (BOLA 403)")
    void getOrderById_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";

        assertThatThrownBy(() -> orderService.getOrderById(501L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("DEF-03: Creator supervisor can access unassigned CREATED order (task.supervisor is null)")
    void getOrderById_byCreatorSupervisor_mustSucceed() {
        currentUsername = "supervisor_alice";

        OrderResponse response = orderService.getOrderById(501L);
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(501L);
        assertThat(aliceOrder.getCreatedBy()).isEqualTo("supervisor_alice");
    }

    @Test
    @DisplayName("DEF-03: Task supervisor can access order even if created by another user")
    void getOrderById_byTaskSupervisor_mustSucceed() {
        // Order was created by Bob
        aliceOrder.setCreatedBy("supervisor_bob");

        // But Alice is supervisor of a task linked to this order
        taskSupervisorUsernames.add("supervisor_alice");
        currentUsername = "supervisor_alice";

        OrderResponse response = orderService.getOrderById(501L);
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(501L);
    }

    @Test
    @DisplayName("DEF-03: ROLE_DEV user can access order regardless of creator or task supervisor")
    void getOrderById_byDevUser_mustBypassOwnershipCheck() {
        currentUsername = "developer_dave";
        isDevUser = true;

        OrderResponse response = orderService.getOrderById(501L);
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(501L);
    }

    @Test
    @DisplayName("DEF-03: Supervisor cannot update order of another supervisor (BOLA 403)")
    void updateOrder_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";
        OrderUpdateRequest updateRequest = new OrderUpdateRequest("NEW_LOGIC", 10L, OrderStatus.CREATED);

        assertThatThrownBy(() -> orderService.updateOrder(501L, updateRequest))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("DEF-03: Supervisor cannot delete order of another supervisor (BOLA 403)")
    void deleteOrderById_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";

        assertThatThrownBy(() -> orderService.deleteOrderById(501L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("DEF-03: Supervisor cannot assign order of another supervisor (BOLA 403)")
    void assignOrder_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";

        assertThatThrownBy(() -> orderService.assignOrder(501L, 999L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("DEF-03: Supervisor cannot access shortage details of another supervisor's order (BOLA 403)")
    void getShortageDetails_byAttackingSupervisor_mustThrowAccessDeniedException() {
        currentUsername = "supervisor_mallory";

        assertThatThrownBy(() -> orderService.getShortageDetails(501L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("belongs to another supervisor");
    }
}
