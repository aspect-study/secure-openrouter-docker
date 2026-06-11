package com.openrouter.gateway.orchestrator;

import com.openrouter.gateway.auth.User;
import com.openrouter.gateway.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orchestrate")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class OrchestratorController {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorController.class);

    private final OrchestratorService orchestratorService;
    private final UserRepository userRepository;

    public OrchestratorController(OrchestratorService orchestratorService,
                                   UserRepository userRepository) {
        this.orchestratorService = orchestratorService;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody OrchestrateRequest request,
                              @AuthenticationPrincipal String email) {
        SseEmitter emitter = new SseEmitter(120_000L);
        User user = resolveUser(email);
        boolean isAdmin = user.getRole() == User.Role.ADMIN;

        Thread.ofVirtual().start(() ->
                orchestratorService.stream(request.prompt(), user.getId(), isAdmin, email, emitter));

        return emitter;
    }

    @PostMapping("/synthesize")
    public ResponseEntity<SynthesisResponse> synthesize(
            @Valid @RequestBody SynthesisRequest request,
            @AuthenticationPrincipal String email) {
        User user = resolveUser(email);
        boolean isAdmin = user.getRole() == User.Role.ADMIN;
        SynthesisResponse response = orchestratorService.synthesize(request, email, user.getId(), isAdmin);
        return ResponseEntity.ok(response);
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
