package com.isd.wms.service.ai;

import com.isd.wms.entity.Order;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.entity.Task;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.repository.OrderRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Enforces server-side authorization boundaries, two-phase confirmation protocol,
 * object-level access control, and security audit logging for all mutating AI tools.
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
        Long targetEntityId,
        String details,
        Instant expiresAt
    ) {
        public PendingConfirmation(String token, String username, String actionType, String targetId, String details, Instant expiresAt) {
            this(token, username, actionType, targetId, null, details, expiresAt);
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final SecurityFacade securityFacade;
    private final OrderRepository orderRepository;
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
     * Returns true if the currently authenticated user has the DEV role.
     */
    public boolean hasDevRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
            .anyMatch(a -> {
                String authority = a.getAuthority();
                return "ROLE_DEV".equalsIgnoreCase(authority) ||
                       "DEV".equalsIgnoreCase(authority);
            });
    }

    /**
     * Enforces object-level authorization for an Order target.
     * DEV role can manage any order.
     * SUPERVISOR role can only manage orders they supervise. If the order has assigned supervisor(s)
     * and none match the authenticated caller, access is denied.
     *
     * @param order target order
     * @throws AccessDeniedException if caller lacks permission for this order
     */
    public void enforceOrderAccess(Order order) {
        enforceSupervisorOrDev("orderAccess");
        if (hasDevRole()) {
            return;
        }

        String currentUsername = securityFacade.getCurrentUsername();
        boolean isCreator = order.getCreatedBy() != null && order.getCreatedBy().equalsIgnoreCase(currentUsername);
        List<String> supervisors = orderRepository.findSupervisorUsernamesByOrder(order);
        boolean isTaskSupervisor = supervisors.stream().anyMatch(s -> s.equalsIgnoreCase(currentUsername));

        if (!isCreator && !isTaskSupervisor && (!supervisors.isEmpty() || order.getCreatedBy() != null)) {
            AUDIT_LOG.warn("OBJECT_ACCESS_DENIED: Supervisor [{}] attempted to access order [{}] belonging to supervisor(s) {}",
                currentUsername, order.getLogicId(), supervisors);
            throw new AccessDeniedException("Access denied: Order '" + order.getLogicId() +
                "' belongs to another supervisor.");
        }
    }

    /**
     * Enforces object-level authorization for a Replenishment target.
     * DEV role can manage any replenishment.
     * SUPERVISOR role can only manage replenishments they supervise or created.
     *
     * @param replenishment target replenishment
     * @throws AccessDeniedException if caller lacks permission for this replenishment
     */
    public void enforceReplenishmentAccess(Replenishment replenishment) {
        enforceSupervisorOrDev("replenishmentAccess");
        if (hasDevRole()) {
            return;
        }

        String currentUsername = securityFacade.getCurrentUsername();
        boolean isCreator = replenishment.getCreatedBy() != null && replenishment.getCreatedBy().equalsIgnoreCase(currentUsername);
        String supervisorUsername = replenishment.getTask()
            .map(Task::getSupervisor)
            .map(User::getUsername)
            .orElse(null);
        boolean isTaskSupervisor = supervisorUsername != null && supervisorUsername.equalsIgnoreCase(currentUsername);

        if (!isCreator && !isTaskSupervisor && (supervisorUsername != null || replenishment.getCreatedBy() != null)) {
            AUDIT_LOG.warn("OBJECT_ACCESS_DENIED: Supervisor [{}] attempted to access replenishment [{}] belonging to supervisor [{}]",
                currentUsername, replenishment.getLogicId(), supervisorUsername);
            throw new AccessDeniedException("Access denied: Replenishment '" + replenishment.getLogicId() +
                "' belongs to another supervisor.");
        }
    }

    /**
     * Enforces that the target user for assignment exists, has ROLE_OPERATOR, and is active.
     *
     * @param operator target operator user
     * @throws AccessDeniedException if the target user is not an active operator
     */
    public void enforceTargetOperator(User operator) {
        if (operator == null) {
            throw new AccessDeniedException("Target operator does not exist.");
        }
        if (operator.getUserRole() != Role.ROLE_OPERATOR) {
            AUDIT_LOG.warn("OBJECT_ACCESS_DENIED: Attempted to assign task to non-operator user [{}] with role [{}]",
                operator.getUsername(), operator.getUserRole());
            throw new AccessDeniedException("Access denied: User '" + operator.getUsername() + "' is not an operator.");
        }
        if (!Boolean.TRUE.equals(operator.getIsActive())) {
            AUDIT_LOG.warn("OBJECT_ACCESS_DENIED: Attempted to assign task to inactive operator [{}]", operator.getUsername());
            throw new AccessDeniedException("Access denied: Operator '" + operator.getUsername() + "' is inactive.");
        }
    }

    /**
     * Initiates a pending operation requiring out-of-band human confirmation.
     * The model is given a descriptive message with the operation ID, but CANNOT confirm it autonomously.
     *
     * @param actionType     name of the action (e.g. DELETE_ORDER, ADJUST_INVENTORY_STOCK)
     * @param targetId       business identifier of the target (e.g. order logicId, barcode@location)
     * @param targetEntityId internal database ID of the target entity (optional)
     * @param details        human-readable description of the pending change
     * @return confirmation prompt string explaining that human supervisor confirmation is required
     */
    public String initiatePendingOperation(String actionType, String targetId, Long targetEntityId, String details) {
        enforceSupervisorOrDev(actionType);
        String username = securityFacade.getCurrentUsername();
        cleanExpiredConfirmations();

        String operationId = "OP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PendingConfirmation pending = new PendingConfirmation(
            operationId,
            username,
            actionType,
            targetId,
            targetEntityId,
            details,
            Instant.now().plus(5, ChronoUnit.MINUTES)
        );
        pendingConfirmations.put(operationId, pending);
        AUDIT_LOG.info("PENDING_OPERATION_CREATED: Operation [{}] initiated by user [{}] action [{}] target [{}] ({})",
            operationId, username, actionType, targetId, details);

        return String.format(
            "PENDING HUMAN CONFIRMATION: Action '%s' on target '%s' (%s) requires out-of-band confirmation. " +
            "Operation ID: '%s'. For safety, AI cannot confirm or execute destructive actions autonomously. " +
            "A human supervisor must explicitly confirm this operation via the management interface or confirmation endpoint.",
            actionType, targetId, details, operationId
        );
    }

    /**
     * Confirms and atomically consumes a pending operation by an authenticated human supervisor/DEV.
     *
     * @param operationId ID of the pending operation
     * @return the confirmed {@link PendingConfirmation} record for execution
     * @throws AccessDeniedException if unauthenticated, unauthorized, invalid, or expired
     */
    public PendingConfirmation confirmOperationByHuman(String operationId) {
        enforceSupervisorOrDev("confirmOperation");
        String username = securityFacade.getCurrentUsername();
        cleanExpiredConfirmations();

        String trimmedId = operationId != null ? operationId.trim() : "";
        PendingConfirmation pending = pendingConfirmations.remove(trimmedId);

        if (pending == null || pending.isExpired()) {
            AUDIT_LOG.warn("INVALID_CONFIRMATION: User [{}] provided invalid/expired operation ID [{}]",
                username, trimmedId);
            throw new AccessDeniedException("Invalid or expired operation ID: " + trimmedId);
        }

        if (!hasDevRole() && !pending.username().equalsIgnoreCase(username)) {
            AUDIT_LOG.warn("MISMATCH_CONFIRMATION: User [{}] attempted to confirm operation [{}] initiated by [{}]",
                username, trimmedId, pending.username());
            throw new AccessDeniedException("Access denied: You are not authorized to confirm operation initiated by " + pending.username());
        }

        AUDIT_LOG.info("HUMAN_CONFIRMED_EXECUTION: User [{}] confirmed action [{}] on target [{}] (op: [{}])",
            username, pending.actionType(), pending.targetId(), trimmedId);
        return pending;
    }

    /**
     * Rejects and removes a pending operation.
     */
    public PendingConfirmation rejectOperationByHuman(String operationId) {
        enforceSupervisorOrDev("rejectOperation");
        String username = securityFacade.getCurrentUsername();

        String trimmedId = operationId != null ? operationId.trim() : "";
        PendingConfirmation pending = pendingConfirmations.remove(trimmedId);

        if (pending == null) {
            throw new AccessDeniedException("Operation ID not found: " + trimmedId);
        }

        AUDIT_LOG.info("HUMAN_REJECTED_OPERATION: User [{}] rejected action [{}] on target [{}] (op: [{}])",
            username, pending.actionType(), pending.targetId(), trimmedId);
        return pending;
    }

    /**
     * Returns all currently active pending operations visible to the authenticated caller.
     */
    public List<PendingConfirmation> getPendingOperations() {
        enforceSupervisorOrDev("getPendingOperations");
        cleanExpiredConfirmations();
        String username = securityFacade.getCurrentUsername();
        boolean isDev = hasDevRole();

        return pendingConfirmations.values().stream()
            .filter(p -> !p.isExpired())
            .filter(p -> isDev || p.username().equalsIgnoreCase(username))
            .toList();
    }

    /**
     * Backward-compatible confirmation method for existing tools.
     * <p>
     * If confirmationToken is omitted (null/blank), creates a pending operation awaiting human approval.
     * If a confirmationToken is attempted in the tool call, it is strictly REJECTED to prevent
     * autonomous LLM confirmation loops.
     * </p>
     */
    public String requireConfirmation(String actionType, String targetId, String details, String confirmationToken) {
        enforceSupervisorOrDev(actionType);
        cleanExpiredConfirmations();

        if (confirmationToken != null && !confirmationToken.trim().isEmpty()) {
            AUDIT_LOG.warn("AUTONOMOUS_TOOL_CONFIRMATION_BLOCKED: AI attempted to pass confirmation token [{}] for action [{}] target [{}]",
                confirmationToken.trim(), actionType, targetId);
            throw new AccessDeniedException("Autonomous AI tool confirmation is disabled. Destructive operations require human approval via trusted management endpoint.");
        }

        return initiatePendingOperation(actionType, targetId, null, details);
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
