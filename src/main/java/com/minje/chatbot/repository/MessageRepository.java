package com.minje.chatbot.repository;

import com.minje.chatbot.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Page<Message> findByConversationId(Long conversationId, Pageable pageable);

    Optional<Message> findByIdAndConversationId(Long id, Long conversationId);

    long countByConversationId(Long conversationId);

    void deleteByIdAndConversationId(Long id, Long conversationId);
}
