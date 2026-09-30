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

/**
 * REST controller for AI-powered chat interactions within the WMS.
 *
 * <p>Exposes an endpoint for authorized users to submit natural language questions
 * and receive AI-generated responses via the underlying {@link ChatbotService}.
 * Access is restricted to users with the {@code SUPERVISOR} or {@code DEV} role.</p>
 *
 * <p>Base path: {@code /api/v1/chat}</p>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final ChatbotService chatbotService;


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
}
