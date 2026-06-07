package com.openrouter.gateway.admin;

import com.openrouter.gateway.agent.AgentService;
import com.openrouter.gateway.agent.model.AgentRequest;
import com.openrouter.gateway.agent.model.AgentResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * Admin-only endpoint for the Gateway Intelligence Agent.
 * Requires ROLE_ADMIN and a configured BYOK OpenRouter API key.
 */
@RestController
@RequestMapping("/api/agent")
@PreAuthorize("hasRole('ADMIN')")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * POST /api/agent/chat
     *
     * Runs the ReAct agent loop for the given question.
     * Returns the agent's final reply plus the tool invocation trace.
     *
     * Response 200: AgentResponse {reply, toolSteps}
     * Response 400: validation error (missing/blank question)
     * Response 409: KeyNotConfiguredException — admin has no BYOK key configured
     */
    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody AgentRequest request,
                              @AuthenticationPrincipal String email) {
        return agentService.run(request, email);
    }
}
