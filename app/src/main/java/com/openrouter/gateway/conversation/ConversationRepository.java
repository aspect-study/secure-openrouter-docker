package com.openrouter.gateway.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserEmailOrderByUpdatedAtDesc(String userEmail);

    Optional<Conversation> findByIdAndUserEmail(Long id, String userEmail);
}
