package com.openrouter.gateway.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelUsageLimitRepository extends JpaRepository<ModelUsageLimit, Long> {

    /** Global default for a model (user_id IS NULL). */
    Optional<ModelUsageLimit> findByModelIdAndUserIsNull(String modelId);

    /** User-specific override for a model. */
    Optional<ModelUsageLimit> findByModelIdAndUserId(String modelId, Long userId);

    /** All global defaults (for admin listing). */
    List<ModelUsageLimit> findByUserIsNull();

    /** All overrides for a specific user (for admin listing). */
    List<ModelUsageLimit> findByUserId(Long userId);

    /** Delete user-specific override. */
    void deleteByModelIdAndUserId(String modelId, Long userId);
}
