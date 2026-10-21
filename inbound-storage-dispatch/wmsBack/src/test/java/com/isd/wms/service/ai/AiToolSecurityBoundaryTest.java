package com.isd.wms.service.ai;

import com.isd.wms.entity.Order;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.entity.Task;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiToolSecurityBoundaryTest {

    @Mock
    private SecurityFacade securityFacade;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AiToolSecurityBoundary securityBoundary;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        securityBoundary.clearPendingConfirmations();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        securityBoundary.clearPendingConfirmations();
    }

    private void authenticateAs(String username, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            username,
            "password",
            List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("enforceSupervisorOrDev throws AccessDeniedException when unauthenticated")
    void enforceSupervisorOrDev_whenUnauthenticated_throwsAccessDeniedException() {
        assertThatThrownBy(() -> securityBoundary.enforceSupervisorOrDev("deleteOrder"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Unauthenticated access to mutating AI tool: deleteOrder");
    }

    @Test
    @DisplayName("enforceSupervisorOrDev throws AccessDeniedException for ROLE_OPERATOR")
    void enforceSupervisorOrDev_whenOperatorRole_throwsAccessDeniedException() {
        authenticateAs("operator_joe", "ROLE_OPERATOR");

        assertThatThrownBy(() -> securityBoundary.enforceSupervisorOrDev("deleteOrder"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("lacks required SUPERVISOR or DEV role");
    }

    @Test
    @DisplayName("enforceSupervisorOrDev succeeds for ROLE_SUPERVISOR")
    void enforceSupervisorOrDev_whenSupervisorRole_succeeds() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");

        // Should not throw
        securityBoundary.enforceSupervisorOrDev("deleteOrder");
        assertThat(securityBoundary.hasSupervisorOrDevRole()).isTrue();
    }

    @Test
    @DisplayName("enforceSupervisorOrDev succeeds for ROLE_DEV")
    void enforceSupervisorOrDev_whenDevRole_succeeds() {
        authenticateAs("dev_alice", "ROLE_DEV");

        securityBoundary.enforceSupervisorOrDev("createOrder");
        assertThat(securityBoundary.hasSupervisorOrDevRole()).isTrue();
    }

    @Test
    @DisplayName("requireConfirmation creates pending confirmation with 15-minute TTL and returns human confirmation prompt")
    void requireConfirmation_whenNoToken_createsPendingConfirmationAndReturnsPrompt() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");

        String prompt = securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-1234",
            "Delete order ORD-1234",
            null
        );

        assertThat(prompt).contains("PENDING HUMAN CONFIRMATION: Action 'DELETE_ORDER' on target 'ORD-1234'");
        assertThat(prompt).contains("Operation ID: 'OP-");

        List<AiToolSecurityBoundary.PendingConfirmation> pending = securityBoundary.getPendingOperations();
        assertThat(pending).hasSize(1);
        AiToolSecurityBoundary.PendingConfirmation op = pending.get(0);
        assertThat(op.actionType()).isEqualTo("DELETE_ORDER");
        assertThat(op.targetId()).isEqualTo("ORD-1234");
        assertThat(op.username()).isEqualTo("supervisor_dan");

        // Verify 15-minute TTL
        java.time.Instant now = java.time.Instant.now();
        assertThat(op.expiresAt())
            .isAfter(now.plus(14, java.time.temporal.ChronoUnit.MINUTES))
            .isBefore(now.plus(16, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("requireConfirmation strictly blocks autonomous confirmation when token is attempted in tool call")
    void requireConfirmation_whenAutonomousTokenAttempted_throwsAccessDeniedException() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");

        assertThatThrownBy(() -> securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-1234",
            "Delete order ORD-1234",
            "CONFIRM-FAKE1234"
        )).isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Autonomous AI tool confirmation is disabled");
    }

    @Test
    @DisplayName("confirmOperationByHuman consumes pending operation and prevents replay")
    void confirmOperationByHuman_whenValid_consumesOperationAndPreventsReplay() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");

        String prompt = securityBoundary.initiatePendingOperation(
            "DELETE_ORDER",
            "ORD-1234",
            101L,
            "Delete order ORD-1234"
        );

        int idStart = prompt.indexOf("Operation ID: '") + "Operation ID: '".length();
        int idEnd = prompt.indexOf("'", idStart);
        String operationId = prompt.substring(idStart, idEnd);

        // Human confirms operation
        AiToolSecurityBoundary.PendingConfirmation confirmed = securityBoundary.confirmOperationByHuman(operationId);
        assertThat(confirmed).isNotNull();
        assertThat(confirmed.targetId()).isEqualTo("ORD-1234");
        assertThat(confirmed.targetEntityId()).isEqualTo(101L);

        // Replay must be rejected
        assertThatThrownBy(() -> securityBoundary.confirmOperationByHuman(operationId))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Invalid or expired operation ID");
    }

    @Test
    @DisplayName("confirmOperationByHuman rejects confirmation from another supervisor unless DEV")
    void confirmOperationByHuman_whenDifferentSupervisor_throwsAccessDeniedException() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");

        String prompt = securityBoundary.initiatePendingOperation(
            "DELETE_ORDER",
            "ORD-1234",
            101L,
            "Delete order ORD-1234"
        );
        String operationId = prompt.substring(prompt.indexOf("OP-"), prompt.indexOf("'. For"));

        // Switch to different supervisor
        authenticateAs("supervisor_mallory", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_mallory");

        assertThatThrownBy(() -> securityBoundary.confirmOperationByHuman(operationId))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("You are not authorized to confirm operation initiated by supervisor_dan");
    }

    @Test
    @DisplayName("withSecurityContext propagates security context across thread boundaries")
    void withSecurityContext_propagatesContextToAnotherThread() throws ExecutionException, InterruptedException {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        SecurityContext captured = SecurityContextHolder.getContext();

        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
            securityBoundary.withSecurityContext(captured, () -> {
                securityBoundary.enforceSupervisorOrDev("asyncTool");
                return securityBoundary.hasSupervisorOrDevRole();
            })
        );

        assertThat(future.get()).isTrue();
    }

    @Test
    @DisplayName("enforceOrderAccess allows DEV caller to access any order")
    void enforceOrderAccess_whenDevCaller_allowsAccess() {
        authenticateAs("dev_user", "ROLE_DEV");
        Order order = new Order("ORD-001");

        // Should not throw
        securityBoundary.enforceOrderAccess(order);
    }

    @Test
    @DisplayName("enforceOrderAccess allows supervisor to access order supervised by themselves")
    void enforceOrderAccess_whenSupervisorOwnsOrder_allowsAccess() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");
        Order order = new Order("ORD-001");
        when(orderRepository.findSupervisorUsernamesByOrder(order)).thenReturn(List.of("supervisor_dan"));

        // Should not throw
        securityBoundary.enforceOrderAccess(order);
    }

    @Test
    @DisplayName("enforceOrderAccess allows supervisor to access order with no supervisor assigned yet")
    void enforceOrderAccess_whenOrderHasNoSupervisor_allowsAccess() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        Order order = new Order("ORD-001");
        when(orderRepository.findSupervisorUsernamesByOrder(order)).thenReturn(List.of());

        // Should not throw
        securityBoundary.enforceOrderAccess(order);
    }

    @Test
    @DisplayName("enforceOrderAccess denies supervisor attempting to access another supervisor's order")
    void enforceOrderAccess_whenOrderBelongsToOtherSupervisor_throwsAccessDeniedException() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");
        Order order = new Order("ORD-OTHER");
        when(orderRepository.findSupervisorUsernamesByOrder(order)).thenReturn(List.of("supervisor_alice"));

        assertThatThrownBy(() -> securityBoundary.enforceOrderAccess(order))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("enforceReplenishmentAccess allows DEV caller to access any replenishment")
    void enforceReplenishmentAccess_whenDevCaller_allowsAccess() {
        authenticateAs("dev_user", "ROLE_DEV");
        Replenishment repl = new Replenishment();
        repl.setLogicId("REPL-001");

        // Should not throw
        securityBoundary.enforceReplenishmentAccess(repl);
    }

    @Test
    @DisplayName("enforceReplenishmentAccess allows supervisor to access replenishment they supervise")
    void enforceReplenishmentAccess_whenSupervisorOwnsReplenishment_allowsAccess() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");
        Replenishment repl = new Replenishment();
        repl.setLogicId("REPL-001");
        User supervisor = new User();
        supervisor.setUsername("supervisor_dan");
        Task task = new Task();
        task.setSupervisor(supervisor);
        repl.setTask(task);

        // Should not throw
        securityBoundary.enforceReplenishmentAccess(repl);
    }

    @Test
    @DisplayName("enforceReplenishmentAccess denies supervisor attempting to access another supervisor's replenishment")
    void enforceReplenishmentAccess_whenBelongsToOtherSupervisor_throwsAccessDeniedException() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");
        Replenishment repl = new Replenishment();
        repl.setLogicId("REPL-OTHER");
        User supervisor = new User();
        supervisor.setUsername("supervisor_alice");
        Task task = new Task();
        task.setSupervisor(supervisor);
        repl.setTask(task);

        assertThatThrownBy(() -> securityBoundary.enforceReplenishmentAccess(repl))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("belongs to another supervisor");
    }

    @Test
    @DisplayName("enforceTargetOperator allows active operator")
    void enforceTargetOperator_whenActiveOperator_allows() {
        User operator = new User();
        operator.setUsername("operator_joe");
        operator.setUserRole(Role.ROLE_OPERATOR);
        operator.setIsActive(true);

        // Should not throw
        securityBoundary.enforceTargetOperator(operator);
    }

    @Test
    @DisplayName("enforceTargetOperator throws AccessDeniedException when operator is inactive")
    void enforceTargetOperator_whenInactiveOperator_throwsAccessDeniedException() {
        User operator = new User();
        operator.setUsername("operator_joe");
        operator.setUserRole(Role.ROLE_OPERATOR);
        operator.setIsActive(false);

        assertThatThrownBy(() -> securityBoundary.enforceTargetOperator(operator))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("is inactive");
    }

    @Test
    @DisplayName("enforceTargetOperator throws AccessDeniedException when user is not an operator")
    void enforceTargetOperator_whenNonOperator_throwsAccessDeniedException() {
        User supervisor = new User();
        supervisor.setUsername("supervisor_bob");
        supervisor.setUserRole(Role.ROLE_SUPERVISOR);
        supervisor.setIsActive(true);

        assertThatThrownBy(() -> securityBoundary.enforceTargetOperator(supervisor))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("is not an operator");
    }

    @Test
    @DisplayName("enforceTargetOperator throws AccessDeniedException when user is null")
    void enforceTargetOperator_whenNull_throwsAccessDeniedException() {
        assertThatThrownBy(() -> securityBoundary.enforceTargetOperator(null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Target operator does not exist");
    }
}
