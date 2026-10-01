package com.isd.wms.service.ai;

import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Enforces server-side authorization boundaries, two-phase confirmation protocol,
 * and security audit logging for all mutating AI tools.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolSecurityBoundary {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AI_TOOL_AUDIT");

    public record PendingConfirmation(
        String token,
        String username,
        String actionType,
        String targetId,
        String details,
        Instant expiresAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final SecurityFacade securityFacade;
    private final ConcurrentMap<String, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    /**
     * Enforces that the current invocation context is authenticated and possesses
     * either the SUPERVISOR or DEV role.
     *
     * @param toolName name of the AI tool being executed
     * @throws AccessDeniedException if unauthenticated or unauthorized
     */
    public void enforceSupervisorOrDev(String toolName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            AUDIT_LOG.warn("UNAUTHENTICATED invocation attempt for AI tool: {}", toolName);
            throw new AccessDeniedException("Unauthenticated access to mutating AI tool: " + toolName);
        }

        if (!hasSupervisorOrDevRole()) {
            AUDIT_LOG.warn("UNAUTHORIZED invocation attempt for AI tool '{}' by user '{}' with authorities {}",
                toolName, auth.getName(), auth.getAuthorities());
            throw new AccessDeniedException("Access denied: User '" + auth.getName() +
                "' lacks required SUPERVISOR or DEV role for tool: " + toolName);
        }
    }

    /**
     * Returns true if the currently authenticated user has the SUPERVISOR or DEV role.
     */
    public boolean hasSupervisorOrDevRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
            .anyMatch(a -> {
                String authority = a.getAuthority();
                return "ROLE_SUPERVISOR".equalsIgnoreCase(authority) ||
                       "SUPERVISOR".equalsIgnoreCase(authority) ||
                       "ROLE_DEV".equalsIgnoreCase(authority) ||
                       "DEV".equalsIgnoreCase(authority);
            });
    }

    /**
     * Enforces a two-phase confirmation boundary for high-impact mutations.
     * <p>
     * If the confirmationToken is omitted (null or blank), a unique pending confirmation
     * token is generated and returned with a descriptive message. The mutation MUST NOT execute.
     * If a confirmationToken is provided, it is validated against the authenticated user,
     * action type, and target ID. If valid, the token is consumed (single-use) and null is returned,
     * signaling the caller to proceed with the mutation.
     * </p>
     *
     * @param actionType        name of the action (e.g. DELETE_ORDER, ADJUST_INVENTORY_STOCK)
     * @param targetId          business identifier of the target (e.g. order logicId, barcode@location)
     * @param details           human-readable description of the pending change
     * @param confirmationToken optional token provided by the user in the follow-up prompt
     * @return confirmation prompt string if confirmation is required; null if token was validly consumed
     * @throws AccessDeniedException if token is invalid, expired, or issued for a different action/user
     */
    public String requireConfirmation(String actionType, String targetId, String details, String confirmationToken) {
        enforceSupervisorOrDev(actionType);
        String username = securityFacade.getCurrentUsername();
        cleanExpiredConfirmations();

        if (confirmationToken == null || confirmationToken.trim().isEmpty()) {
            String token = "CONFIRM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            PendingConfirmation pending = new PendingConfirmation(
                token,
                username,
                actionType,
                targetId,
                details,
                Instant.now().plus(5, ChronoUnit.MINUTES)
            );
            pendingConfirmations.put(token, pending);
            AUDIT_LOG.info("PENDING_CONFIRMATION: Token [{}] issued for user [{}] action [{}] target [{}] ({})",
                token, username, actionType, targetId, details);

            return String.format(
                "CONFIRMATION REQUIRED: %s on target '%s' (%s) is a high-impact operation. " +
                "To confirm and execute, call this tool with confirmationToken='%s'.",
                actionType, targetId, details, token
            );
        }

        String trimmedToken = confirmationToken.trim();
        PendingConfirmation pending = pendingConfirmations.remove(trimmedToken);

        if (pending == null || pending.isExpired()) {
            AUDIT_LOG.warn("INVALID_CONFIRMATION: User [{}] provided invalid/expired token [{}] for action [{}] target [{}]",
                username, trimmedToken, actionType, targetId);
            throw new AccessDeniedException("Invalid or expired confirmation token: " + trimmedToken);
        }

        if (!pending.username().equalsIgnoreCase(username) ||
            !pending.actionType().equalsIgnoreCase(actionType) ||
            !pending.targetId().equalsIgnoreCase(targetId)) {
            AUDIT_LOG.warn("MISMATCH_CONFIRMATION: User [{}] attempted token [{}] issued for user [{}] action [{}] target [{}]",
                username, trimmedToken, pending.username(), pending.actionType(), pending.targetId());
            throw new AccessDeniedException("Confirmation token does not match the requested action, user, or target.");
        }

        AUDIT_LOG.info("CONFIRMED_EXECUTION: User [{}] confirmed action [{}] on target [{}] using token [{}]",
            username, actionType, targetId, trimmedToken);
        return null; // Confirmed, proceed
    }

    /**
     * Emits a structured security audit log for mutating AI tool executions.
     */
    public void auditMutation(String toolName, String targetId, String details) {
        String username = "UNKNOWN";
        try {
            username = securityFacade.getCurrentUsername();
        } catch (Exception ignored) {}
        AUDIT_LOG.info("AI_MUTATION: Tool [{}] executed by user [{}] on target [{}]: {}",
            toolName, username, targetId, details);
    }

    /**
     * Helper to run an action with an explicitly supplied SecurityContext (for async/thread-pool boundaries).
     */
    public <T> T withSecurityContext(SecurityContext context, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(context);
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    public void clearPendingConfirmations() {
        pendingConfirmations.clear();
    }

    private void cleanExpiredConfirmations() {
        pendingConfirmations.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
