package com.minje.chatbot.repository;

import com.minje.chatbot.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Page<Conversation> findByUserId(Long userId, Pageable pageable);
}
