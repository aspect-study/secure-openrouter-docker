package com.openrouter.gateway.logging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {

    List<ChatLog> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    @Query("SELECT SUM(c.totalTokens) FROM ChatLog c WHERE c.userEmail = :userEmail")
    Long sumTotalTokensByUserEmail(String userEmail);
}
