package com.minje.chatbot.repository;

import com.minje.chatbot.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Page<Conversation> findByUserId(Long userId, Pageable pageable);

    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages WHERE c.id = :id AND c.userId = :userId")
    Optional<Conversation> findByIdAndUserIdWithMessages(@Param("id") Long id, @Param("userId") Long userId);

    long countByUserId(Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
