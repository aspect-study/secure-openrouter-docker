package com.openrouter.gateway.apikey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for managing the authenticated user's OpenRouter API key.
 *
 * All endpoints require a valid JWT. Users can only manage their own key.
 * The plaintext key is NEVER returned in any response — status only.
 */
@RestController
@RequestMapping("/api/user/api-key")
public class ApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyController.class);

    private final OpenRouterKeyService openRouterKeyService;

    public ApiKeyController(OpenRouterKeyService openRouterKeyService) {
        this.openRouterKeyService = openRouterKeyService;
    }

    /**
     * Save or update the user's OpenRouter API key.
     * Key is validated against OpenRouter before storing.
     *
     * PUT /api/user/api-key
     * Body: {"apiKey": "sk-or-v1-..."}
     * Response 200: {"configured": true}
     * Response 400: {"error": "Invalid OpenRouter API key..."}
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> saveKey(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) throws Exception {

        String rawKey = body.get("apiKey");
        String userEmail = userDetails.getUsername();

        log.info("API key save request from user: {}", userEmail);
        openRouterKeyService.saveKey(userEmail, rawKey);

        return ResponseEntity.ok(Map.of(
                "configured", true,
                "message", "API key saved and validated successfully."
        ));
    }

    /**
     * Remove the user's stored API key.
     *
     * DELETE /api/user/api-key
     * Response 200: {"configured": false}
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> removeKey(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userEmail = userDetails.getUsername();
        openRouterKeyService.removeKey(userEmail);
        log.info("API key removed for user: {}", userEmail);

        return ResponseEntity.ok(Map.of(
                "configured", false,
                "message", "API key removed."
        ));
    }

    /**
     * Check whether the user has a validated API key configured.
     *
     * GET /api/user/api-key/status
     * Response 200: {"configured": true|false}
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> keyStatus(
            @AuthenticationPrincipal UserDetails userDetails) {

        boolean configured = openRouterKeyService.isKeyConfigured(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("configured", configured));
    }
}
