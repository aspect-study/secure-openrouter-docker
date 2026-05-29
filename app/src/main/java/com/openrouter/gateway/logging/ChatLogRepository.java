package com.openrouter.gateway.logging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {

    List<ChatLog> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    long countByUserEmail(String userEmail);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT COALESCE(SUM(c.totalTokens), 0) FROM ChatLog c WHERE c.createdAt >= :since")
    long sumTotalTokensSince(@Param("since") LocalDateTime since);

    @Query("SELECT c.model FROM ChatLog c WHERE c.createdAt >= :since " +
           "GROUP BY c.model ORDER BY COUNT(c) DESC LIMIT 1")
    String findTopModelToday(@Param("since") LocalDateTime since);

    @Query("SELECT CAST(c.createdAt AS date) AS day, COUNT(c) AS count " +
           "FROM ChatLog c WHERE c.createdAt >= :since " +
           "GROUP BY CAST(c.createdAt AS date) ORDER BY day ASC")
    List<Map<String, Object>> countByDayLast7Days(
            @Param("since") LocalDateTime since);

    default List<Map<String, Object>> countByDayLast7Days() {
        return countByDayLast7Days(LocalDateTime.now().minusDays(7));
    }

    @Query("SELECT c FROM ChatLog c WHERE " +
           "(:user IS NULL OR c.userEmail LIKE %:user%) AND " +
           "(:model IS NULL OR c.model = :model) AND " +
           "(:from IS NULL OR c.createdAt >= :from) AND " +
           "(:to IS NULL OR c.createdAt < :to)")
    Page<ChatLog> findWithFilters(
            @Param("user") String user,
            @Param("model") String model,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT SUM(c.totalTokens) FROM ChatLog c WHERE c.userEmail = :userEmail")
    Long sumTotalTokensByUserEmail(@Param("userEmail") String userEmail);
}
