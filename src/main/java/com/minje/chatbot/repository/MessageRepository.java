package com.minje.chatbot.repository;

import com.minje.chatbot.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<Message> findTop10ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Optional<Message> findFirstByConversationIdAndRoleOrderByCreatedAtAsc(Long conversationId, Message.Role role);

    void deleteAllByConversationId(Long conversationId);
}
