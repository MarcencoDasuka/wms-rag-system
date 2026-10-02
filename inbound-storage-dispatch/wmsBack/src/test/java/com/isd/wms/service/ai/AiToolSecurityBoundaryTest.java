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
    @DisplayName("requireConfirmation generates pending token and returns prompt when token is omitted")
    void requireConfirmation_whenNoToken_createsPendingConfirmationAndReturnsPrompt() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");

        String prompt = securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-1234",
            "Delete order ORD-1234",
            null
        );

        assertThat(prompt).contains("CONFIRMATION REQUIRED: DELETE_ORDER on target 'ORD-1234'");
        assertThat(prompt).contains("confirmationToken='CONFIRM-");
    }

    @Test
    @DisplayName("requireConfirmation rejects invalid or fabricated confirmation token")
    void requireConfirmation_whenInvalidToken_throwsAccessDeniedException() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");

        assertThatThrownBy(() -> securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-1234",
            "Delete order ORD-1234",
            "CONFIRM-FAKE1234"
        )).isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Invalid or expired confirmation token: CONFIRM-FAKE1234");
    }

    @Test
    @DisplayName("requireConfirmation allows execution with valid token and consumes it (single-use)")
    void requireConfirmation_whenValidToken_consumesTokenAndAllowsExecution() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");

        // Phase 1: Request confirmation
        String prompt = securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-1234",
            "Delete order ORD-1234",
            null
        );

        // Extract token
        int tokenStart = prompt.indexOf("confirmationToken='") + "confirmationToken='".length();
        int tokenEnd = prompt.indexOf("'", tokenStart);
        String token = prompt.substring(tokenStart, tokenEnd);
        assertThat(token).startsWith("CONFIRM-");

        // Phase 2: Provide token -> returns null indicating confirmed
        String result = securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-1234",
            "Delete order ORD-1234",
            token
        );
        assertThat(result).isNull();

        // Phase 3: Attempting replay with the same token MUST be rejected
        assertThatThrownBy(() -> securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-1234",
            "Delete order ORD-1234",
            token
        )).isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Invalid or expired confirmation token");
    }

    @Test
    @DisplayName("requireConfirmation rejects token when target ID does not match")
    void requireConfirmation_whenTargetMismatch_throwsAccessDeniedException() {
        authenticateAs("supervisor_dan", "ROLE_SUPERVISOR");
        when(securityFacade.getCurrentUsername()).thenReturn("supervisor_dan");

        String prompt = securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-1234",
            "Delete order ORD-1234",
            null
        );
        String token = prompt.substring(prompt.indexOf("CONFIRM-"), prompt.indexOf("'."));

        // Attempt using token on a different order target
        assertThatThrownBy(() -> securityBoundary.requireConfirmation(
            "DELETE_ORDER",
            "ORD-DIFFERENT",
            "Delete order ORD-DIFFERENT",
            token
        )).isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Confirmation token does not match the requested action, user, or target.");
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
