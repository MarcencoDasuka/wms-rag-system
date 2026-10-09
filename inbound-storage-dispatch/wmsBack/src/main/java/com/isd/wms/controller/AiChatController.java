package com.isd.wms.controller;

import com.isd.wms.dto.ai.AiChatRequest;
import com.isd.wms.dto.ai.AiChatResponse;
import com.isd.wms.service.ai.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.isd.wms.entity.Order;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.ai.AiToolSecurityBoundary;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * REST controller for AI-powered chat interactions within the WMS.
 *
 * <p>Exposes an endpoint for authorized users to submit natural language questions
 * and receive AI-generated responses via the underlying {@link ChatbotService}.
 * Also provides a trusted Human-in-the-Loop confirmation interface for high-impact
 * mutations initiated by AI tools.</p>
 *
 * <p>Base path: {@code /api/v1/chat}</p>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final ChatbotService chatbotService;
    private final AiToolSecurityBoundary securityBoundary;
    private final OrderService orderService;
    private final OrderRepository orderRepository;

    /**
     * Submits a message to the AI chatbot and returns its reply.
     *
     * @param request the chat request containing the user's message; must be valid
     * @return an {@link AiChatResponse} containing the AI-generated reply
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        String reply = chatbotService.askQuestion(request.message());
        return ResponseEntity.ok(new AiChatResponse(reply));
    }

    /**
     * Confirms and executes a pending destructive action initiated by AI tool.
     * Requires human supervisor or DEV role, re-validating object-level access directly
     * before execution.
     */
    @PostMapping("/confirmations/{operationId}/confirm")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<Map<String, Object>> confirmOperation(@PathVariable String operationId) {
        AiToolSecurityBoundary.PendingConfirmation pending = securityBoundary.confirmOperationByHuman(operationId);

        if ("DELETE_ORDER".equalsIgnoreCase(pending.actionType())) {
            Order order;
            if (pending.targetEntityId() != null) {
                Long targetId = pending.targetEntityId();
                order = orderRepository.findById(targetId)
                    .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + targetId));
            } else {
                order = orderRepository.findByLogicIdIgnoreCase(pending.targetId())
                    .orElseThrow(() -> new EntityNotFoundException("Order with logic ID " + pending.targetId() + " not found."));
            }

            // Re-enforce object-level authorization right before physical deletion
            securityBoundary.enforceOrderAccess(order);

            orderService.deleteOrderById(order.getId());
            securityBoundary.auditMutation("confirmDeleteOrder", pending.targetId(), "Human confirmed deletion of order " + pending.targetId());

            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "actionType", pending.actionType(),
                "targetId", pending.targetId(),
                "message", "Order " + pending.targetId() + " successfully deleted by authorized human supervisor."
            ));
        }

        return ResponseEntity.ok(Map.of(
            "status", "CONFIRMED",
            "actionType", pending.actionType(),
            "targetId", pending.targetId(),
            "message", "Operation " + operationId + " confirmed."
        ));
    }

    /**
     * Rejects and discards a pending destructive action.
     */
    @PostMapping("/confirmations/{operationId}/reject")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<Map<String, Object>> rejectOperation(@PathVariable String operationId) {
        AiToolSecurityBoundary.PendingConfirmation rejected = securityBoundary.rejectOperationByHuman(operationId);
        return ResponseEntity.ok(Map.of(
            "status", "REJECTED",
            "actionType", rejected.actionType(),
            "targetId", rejected.targetId(),
            "message", "Operation " + operationId + " was rejected."
        ));
    }

    /**
     * Lists active pending operations awaiting human confirmation.
     */
    @GetMapping("/confirmations/pending")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<AiToolSecurityBoundary.PendingConfirmation>> getPendingConfirmations() {
        return ResponseEntity.ok(securityBoundary.getPendingOperations());
    }
}
