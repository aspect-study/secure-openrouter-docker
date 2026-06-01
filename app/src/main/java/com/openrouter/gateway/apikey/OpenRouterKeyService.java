package com.openrouter.gateway.apikey;

import com.openrouter.gateway.auth.User;
import com.openrouter.gateway.auth.UserRepository;
import com.openrouter.gateway.exception.InvalidApiKeyException;
import com.openrouter.gateway.exception.KeyNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Manages per-user OpenRouter API keys.
 *
 * Keys are validated against the OpenRouter auth endpoint before saving.
 * Storage is encrypted via AesEncryptedStringConverter on the User entity.
 * The plaintext key is NEVER returned from any public API endpoint — only decrypted
 * in-process for forwarding to OpenRouter.
 */
@Service
public class OpenRouterKeyService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterKeyService.class);

    private static final String OPENROUTER_AUTH_URL = "https://openrouter.ai/api/v1/auth/key";

    private final UserRepository userRepository;
    private final HttpClient httpClient;

    public OpenRouterKeyService(UserRepository userRepository, HttpClient httpClient) {
        this.userRepository = userRepository;
        this.httpClient = httpClient;
    }

    /**
     * Validates the key against OpenRouter, then encrypts and saves it.
     *
     * @param userEmail authenticated user's email
     * @param rawKey    plaintext OpenRouter API key (e.g. "sk-or-v1-...")
     * @throws InvalidApiKeyException   if OpenRouter returns a non-200 response
     * @throws IllegalArgumentException if the key is blank
     */
    @Transactional
    public void saveKey(String userEmail, String rawKey) throws Exception {
        if (rawKey == null || rawKey.isBlank()) {
            throw new InvalidApiKeyException("API key must not be blank.");
        }

        // Live validation against OpenRouter — fail fast before storing
        validateKeyWithOpenRouter(rawKey);

        User user = loadUser(userEmail);
        // The converter handles encryption transparently on setOpenrouterKeyEncrypted
        user.setOpenrouterKeyEncrypted(rawKey);
        user.setOpenrouterKeyValidated(true);
        user.setOpenrouterKeySetAt(LocalDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        log.info("OpenRouter API key saved for user: {}", userEmail);
    }

    /**
     * Removes the stored API key for the user.
     */
    @Transactional
    public void removeKey(String userEmail) {
        User user = loadUser(userEmail);
        user.setOpenrouterKeyEncrypted(null);
        user.setOpenrouterKeyValidated(false);
        user.setOpenrouterKeySetAt(null);
        userRepository.save(user);
        log.info("OpenRouter API key removed for user: {}", userEmail);
    }

    /**
     * Returns the decrypted API key for the user — for in-process forwarding only.
     * Never expose this value through any HTTP response.
     *
     * @throws KeyNotConfiguredException if no key is saved or it was not validated
     */
    public String getKeyForUser(String userEmail) {
        User user = loadUser(userEmail);
        if (user.getOpenrouterKeyEncrypted() == null || !user.isOpenrouterKeyValidated()) {
            throw new KeyNotConfiguredException();
        }
        // Converter decrypts transparently via getOpenrouterKeyEncrypted()
        return user.getOpenrouterKeyEncrypted();
    }

    /**
     * Returns whether the user has a validated API key configured.
     */
    public boolean isKeyConfigured(String userEmail) {
        User user = loadUser(userEmail);
        return user.getOpenrouterKeyEncrypted() != null && user.isOpenrouterKeyValidated();
    }

    // ── Private ───────────────────────────────────────────────────────────

    /**
     * Makes a GET request to OpenRouter's auth/key endpoint.
     * Expects HTTP 200 for a valid key; anything else is treated as invalid.
     */
    private void validateKeyWithOpenRouter(String rawKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENROUTER_AUTH_URL))
                .header("Authorization", "Bearer " + rawKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("OpenRouter key validation failed with status {}", response.statusCode());
            throw new InvalidApiKeyException(
                    "Invalid OpenRouter API key — please check your key at openrouter.ai/keys.");
        }

        log.debug("OpenRouter key validation successful (status 200)");
    }

    private User loadUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found in DB: " + userEmail));
    }
}
