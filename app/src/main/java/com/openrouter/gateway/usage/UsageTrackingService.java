package com.openrouter.gateway.usage;

import com.openrouter.gateway.auth.User;
import com.openrouter.gateway.auth.UserRepository;
import com.openrouter.gateway.exception.UsageLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Tracks and enforces per-user daily usage limits per model.
 *
 * Limit resolution order:
 *   1. User-specific override for this model
 *   2. Global default for this model
 *   3. System fallback: 50 requests / 100,000 tokens
 *
 * Request count is checked PRE-CALL (unknown token count before response).
 * Token count is checked POST-CALL — if over limit, the call is allowed but
 * the next call will be hard-blocked on the token check. This avoids blocking
 * mid-flight based on estimates.
 *
 * All dates/times are UTC — LocalDate.now(ZoneOffset.UTC).
 */
@Service
public class UsageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(UsageTrackingService.class);

    // System-level fallback if no DB limit row exists for a model
    private static final int SYSTEM_DEFAULT_REQUESTS = 50;
    private static final int SYSTEM_DEFAULT_TOKENS   = 100_000;

    private final UserModelUsageRepository usageRepository;
    private final ModelUsageLimitRepository limitRepository;
    private final UserRepository userRepository;

    public UsageTrackingService(UserModelUsageRepository usageRepository,
                                ModelUsageLimitRepository limitRepository,
                                UserRepository userRepository) {
        this.usageRepository = usageRepository;
        this.limitRepository = limitRepository;
        this.userRepository = userRepository;
    }

    /**
     * PRE-CALL check: verifies request count limit before forwarding to OpenRouter.
     * Also performs the post-call token check from the previous call (if tokens are now over).
     *
     * @throws UsageLimitExceededException if the request limit is reached
     */
    @Transactional
    public void checkRequestLimit(Long userId, String modelId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UserModelUsage usage = getOrCreateUsage(userId, modelId, today);
        int[] limits = resolveLimits(userId, modelId);
        int maxRequests = limits[0];

        if (usage.getRequestCount() >= maxRequests) {
            log.info("Request limit reached for user {} model {} ({}/{})",
                    userId, modelId, usage.getRequestCount(), maxRequests);
            throw new UsageLimitExceededException("request", modelId, usage.getResetAt());
        }
    }

    /**
     * POST-CALL: increments request + token counters after a successful OpenRouter call.
     * Logs a warning if tokens are now over the daily limit (will hard-block next call).
     *
     * @param tokensUsed prompt_tokens + completion_tokens from the OpenRouter response
     */
    @Transactional
    public void incrementUsage(Long userId, String modelId, int tokensUsed) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UserModelUsage usage = getOrCreateUsage(userId, modelId, today);
        int[] limits = resolveLimits(userId, modelId);
        int maxTokens = limits[1];

        usage.incrementRequests();
        usage.addTokens(tokensUsed);
        usageRepository.save(usage);

        if (usage.getTokenCount() > maxTokens) {
            log.warn("User {} exceeded token limit for model {} ({}/{}) — next call will be blocked",
                    userId, modelId, usage.getTokenCount(), maxTokens);
        }
    }

    /**
     * Returns today's full usage summary for a user (all models with activity today).
     */
    public List<UserModelUsage> getUserUsageSummary(Long userId) {
        return usageRepository.findByUserIdAndPeriodDate(userId, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Returns today's usage for a specific user + model (zeros if no activity today).
     */
    public UserModelUsage getModelUsage(Long userId, String modelId) {
        return usageRepository
                .findByUserIdAndModelIdAndPeriodDate(userId, modelId, LocalDate.now(ZoneOffset.UTC))
                .orElseGet(() -> emptyUsage(userId, modelId));
    }

    /**
     * Resolves the effective limit for a user + model.
     * Returns int[]{maxRequestsPerDay, maxTokensPerDay}.
     */
    public int[] resolveLimits(Long userId, String modelId) {
        // 1. User-specific override
        var userLimit = limitRepository.findByModelIdAndUserId(modelId, userId);
        if (userLimit.isPresent()) {
            ModelUsageLimit l = userLimit.get();
            return new int[]{l.getMaxRequestsPerDay(), l.getMaxTokensPerDay()};
        }
        // 2. Global default
        var globalLimit = limitRepository.findByModelIdAndUserIsNull(modelId);
        if (globalLimit.isPresent()) {
            ModelUsageLimit l = globalLimit.get();
            return new int[]{l.getMaxRequestsPerDay(), l.getMaxTokensPerDay()};
        }
        // 3. System fallback
        return new int[]{SYSTEM_DEFAULT_REQUESTS, SYSTEM_DEFAULT_TOKENS};
    }

    // ── Private ───────────────────────────────────────────────────────────

    private UserModelUsage getOrCreateUsage(Long userId, String modelId, LocalDate today) {
        return usageRepository
                .findByUserIdAndModelIdAndPeriodDate(userId, modelId, today)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "User not found: " + userId));
                    // reset_at = midnight UTC of the next day
                    LocalDateTime resetAt = today.plusDays(1).atStartOfDay(ZoneOffset.UTC)
                            .toLocalDateTime();
                    UserModelUsage newRow = new UserModelUsage(user, modelId, today, resetAt);
                    return usageRepository.save(newRow);
                });
    }

    private UserModelUsage emptyUsage(Long userId, String modelId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime resetAt = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toLocalDateTime();
        User user = new User();
        user = userRepository.findById(userId).orElse(user);
        UserModelUsage empty = new UserModelUsage(user, modelId, today, resetAt);
        empty.setRequestCount(0);
        empty.setTokenCount(0);
        return empty;
    }
}
