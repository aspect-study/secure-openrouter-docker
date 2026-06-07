package com.openrouter.gateway.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.agent.AgentService;
import com.openrouter.gateway.agent.model.AgentRequest;
import com.openrouter.gateway.agent.model.AgentResponse;
import com.openrouter.gateway.exception.AllModelsUnavailableException;
import com.openrouter.gateway.exception.KeyNotConfiguredException;
import com.openrouter.gateway.exception.ModelToolUseNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;

/**
 * Admin-only endpoint for the Gateway Intelligence Agent.
 * Requires ROLE_ADMIN and a configured BYOK OpenRouter API key.
 *
 * POST /api/agent/chat — streams agent progress as SSE, then a final done event.
 *
 * SSE event protocol:
 *   event: status   data: {"type":"trying","model":"...","attempt":N,"total":N}
 *   event: status   data: {"type":"skipped","model":"...","reason":"rate_limited"|"tool_unsupported"}
 *   event: done     data: {"reply":"...","toolSteps":[...],"modelUsed":"..."}
 *   event: error    data: {"error":"...","status":N}
 */
@RestController
@RequestMapping("/api/agent")
@PreAuthorize("hasRole('ADMIN')")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public AgentController(AgentService agentService, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentRequest request,
                           @AuthenticationPrincipal String email) {
        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.ofVirtual().start(() -> {
            try {
                AgentResponse response = agentService.run(request, email, event -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("status")
                                .data(objectMapper.writeValueAsString(event)));
                    } catch (IOException e) {
                        log.debug("Agent SSE client disconnected during status emit");
                    } catch (Exception e) {
                        log.warn("Failed to emit agent status event: {}", e.getMessage());
                    }
                });

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(objectMapper.writeValueAsString(response)));
                emitter.complete();

            } catch (KeyNotConfiguredException e) {
                sendError(emitter, e.getMessage(), 409);
            } catch (AllModelsUnavailableException e) {
                sendError(emitter, e.getMessage(), 503);
            } catch (ModelToolUseNotSupportedException e) {
                sendError(emitter, e.getMessage(), 400);
            } catch (Exception e) {
                log.error("Unexpected error in agent chat: {}", e.getMessage(), e);
                sendError(emitter, "Unexpected error. Please try again.", 500);
            }
        });

        return emitter;
    }

    private void sendError(SseEmitter emitter, String message, int status) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(objectMapper.writeValueAsString(Map.of("error", message, "status", status))));
        } catch (Exception ignored) {}
        emitter.complete();
    }
}
