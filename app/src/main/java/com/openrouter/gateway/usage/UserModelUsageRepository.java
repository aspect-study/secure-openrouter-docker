package com.openrouter.gateway.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserModelUsageRepository extends JpaRepository<UserModelUsage, Long> {

    /** Today's row for a specific user + model. */
    Optional<UserModelUsage> findByUserIdAndModelIdAndPeriodDate(
            Long userId, String modelId, LocalDate date);

    /** All rows for a user on a specific day (full daily summary). */
    List<UserModelUsage> findByUserIdAndPeriodDate(Long userId, LocalDate date);
}
