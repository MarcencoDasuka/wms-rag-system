package com.isd.wms.service.ai;

import com.isd.wms.enums.Role;
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
}
