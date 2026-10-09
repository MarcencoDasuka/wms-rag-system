package com.isd.wms.service.ai;

import com.isd.wms.controller.AiChatController;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.User;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Def01HumanInTheLoopConfirmationTest {

    private AiToolSecurityBoundary securityBoundary;
    private OrderMutatingAiTools orderMutatingAiTools;
    private AiChatController aiChatController;

    private OrderRepository orderRepository;
    private OrderService orderService;
    private SecurityFacade securityFacade;

    private String currentUsername;
    private Order testOrder;
    private final AtomicBoolean orderDeleted = new AtomicBoolean(false);

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private void setSecurityContext(String username, String role) {
        currentUsername = username;
        Authentication auth = new UsernamePasswordAuthenticationToken(
            username,
            "pass",
            List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @BeforeEach
    void setUp() {
        setSecurityContext("supervisor_alice", "ROLE_SUPERVISOR");

        testOrder = new Order("LOGIC-ORD-101");
        ReflectionTestUtils.setField(testOrder, "id", 101L);
        testOrder.setCreatedBy("supervisor_alice");
        testOrder.setStatus(OrderStatus.CREATED);

        orderDeleted.set(false);

        securityFacade = new SecurityFacade(null) {
            @Override
            public String getCurrentUsername() {
                return currentUsername;
            }

            @Override
            public User getCurrentUser() {
                User u = new User(currentUsername, currentUsername + "@isd.com", "pass", Role.ROLE_SUPERVISOR, true, null, null);
                ReflectionTestUtils.setField(u, "id", 1L);
                return u;
            }
        };

        orderRepository = createProxy(OrderRepository.class, (proxy, method, args) -> {
            if ("findById".equals(method.getName())) {
                Long id = (Long) args[0];
                if (Long.valueOf(101L).equals(id)) {
                    return Optional.of(testOrder);
                }
                return Optional.empty();
            }
            if ("findByLogicIdIgnoreCase".equals(method.getName())) {
                String logicId = (String) args[0];
                if ("LOGIC-ORD-101".equalsIgnoreCase(logicId)) {
                    return Optional.of(testOrder);
                }
                return Optional.empty();
            }
            if ("findSupervisorUsernamesByOrder".equals(method.getName())) {
                return List.of("supervisor_alice");
            }
            return null;
        });

        orderService = new OrderService(null, null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public void deleteOrderById(Long orderId) {
                orderDeleted.set(true);
            }
        };

        securityBoundary = new AiToolSecurityBoundary(
            securityFacade,
            orderRepository
        );

        LocationRepository locationRepository = createProxy(LocationRepository.class, (p, m, a) -> null);
        ProductRepository productRepository = createProxy(ProductRepository.class, (p, m, a) -> null);
        UserRepository userRepository = createProxy(UserRepository.class, (p, m, a) -> null);

        orderMutatingAiTools = new OrderMutatingAiTools(
            orderService,
            orderRepository,
            locationRepository,
            productRepository,
            userRepository,
            securityBoundary
        );

        aiChatController = new AiChatController(
            null,
            securityBoundary,
            orderService,
            orderRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("DEF-01: Order deletion initiated by AI tool creates a pending operation and does NOT delete order")
    void aiTool_DeleteOrder_CreatesPendingConfirmation_DoesNotDelete() {
        String response = orderMutatingAiTools.deleteOrder("LOGIC-ORD-101");

        assertThat(response).contains("PENDING HUMAN CONFIRMATION");
        assertThat(response).contains("Operation ID:");
        assertThat(response).contains("LOGIC-ORD-101");
        assertThat(orderDeleted.get()).isFalse();

        List<AiToolSecurityBoundary.PendingConfirmation> pendingList = securityBoundary.getPendingOperations();
        assertThat(pendingList).hasSize(1);
        AiToolSecurityBoundary.PendingConfirmation pending = pendingList.get(0);
        assertThat(pending.actionType()).isEqualTo("DELETE_ORDER");
        assertThat(pending.targetId()).isEqualTo("LOGIC-ORD-101");
        assertThat(pending.targetEntityId()).isEqualTo(101L);
        assertThat(pending.username()).isEqualTo("supervisor_alice");
    }

    @Test
    @DisplayName("DEF-01: AI tool autonomous confirmation via requireConfirmation is strictly blocked")
    void requireConfirmation_BlocksAutonomousAiTokenPassing() {
        assertThatThrownBy(() -> securityBoundary.requireConfirmation("DELETE_ORDER", "LOGIC-ORD-101", "details", "forged-token-123"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Autonomous AI tool confirmation is disabled");

        assertThat(orderDeleted.get()).isFalse();
    }

    @Test
    @DisplayName("DEF-01: Human supervisor confirms operation via management endpoint executing order deletion")
    void humanSupervisor_ConfirmsOperation_ExecutesDeletionAndPreventsReplay() {
        orderMutatingAiTools.deleteOrder("LOGIC-ORD-101");
        List<AiToolSecurityBoundary.PendingConfirmation> pendingList = securityBoundary.getPendingOperations();
        assertThat(pendingList).hasSize(1);
        String operationId = pendingList.get(0).token();

        ResponseEntity<Map<String, Object>> confirmResult = aiChatController.confirmOperation(operationId);

        assertThat(confirmResult.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(confirmResult.getBody()).containsEntry("status", "SUCCESS");
        assertThat(confirmResult.getBody()).containsEntry("actionType", "DELETE_ORDER");
        assertThat(orderDeleted.get()).isTrue();

        // Replay attempt must be rejected (single-use token consumed)
        assertThatThrownBy(() -> aiChatController.confirmOperation(operationId))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Invalid or expired operation ID");
    }

    @Test
    @DisplayName("DEF-01: Operator cannot confirm pending operation (requires SUPERVISOR or DEV)")
    void operator_CannotConfirmPendingOperation() {
        orderMutatingAiTools.deleteOrder("LOGIC-ORD-101");
        String operationId = securityBoundary.getPendingOperations().get(0).token();

        setSecurityContext("operator_bob", "ROLE_OPERATOR");

        assertThatThrownBy(() -> aiChatController.confirmOperation(operationId))
            .isInstanceOf(AccessDeniedException.class);

        assertThat(orderDeleted.get()).isFalse();
    }

    @Test
    @DisplayName("DEF-01: Expired confirmation token cannot be confirmed and fails closed")
    void expiredConfirmation_FailsClosed() {
        orderMutatingAiTools.deleteOrder("LOGIC-ORD-101");
        String operationId = securityBoundary.getPendingOperations().get(0).token();

        // Artificially age the confirmation past 10 minutes
        Map<String, AiToolSecurityBoundary.PendingConfirmation> map =
            (Map<String, AiToolSecurityBoundary.PendingConfirmation>) ReflectionTestUtils.getField(securityBoundary, "pendingConfirmations");
        AiToolSecurityBoundary.PendingConfirmation existing = map.get(operationId);
        AiToolSecurityBoundary.PendingConfirmation expired = new AiToolSecurityBoundary.PendingConfirmation(
            existing.token(),
            existing.username(),
            existing.actionType(),
            existing.targetId(),
            existing.targetEntityId(),
            existing.details(),
            java.time.Instant.now().minusSeconds(60) // 1 minute in the past
        );
        map.put(operationId, expired);

        assertThatThrownBy(() -> aiChatController.confirmOperation(operationId))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Invalid or expired operation ID");

        assertThat(orderDeleted.get()).isFalse();
    }

    @Test
    @DisplayName("DEF-01: Human supervisor rejects pending operation without deletion")
    void humanSupervisor_RejectsPendingOperation_DiscardsWithoutExecuting() {
        orderMutatingAiTools.deleteOrder("LOGIC-ORD-101");
        String operationId = securityBoundary.getPendingOperations().get(0).token();

        ResponseEntity<Map<String, Object>> rejectResult = aiChatController.rejectOperation(operationId);

        assertThat(rejectResult.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rejectResult.getBody()).containsEntry("status", "REJECTED");
        assertThat(orderDeleted.get()).isFalse();
        assertThat(securityBoundary.getPendingOperations()).isEmpty();

        // Subsequent confirmation must fail
        assertThatThrownBy(() -> aiChatController.confirmOperation(operationId))
            .isInstanceOf(AccessDeniedException.class);
    }
}
