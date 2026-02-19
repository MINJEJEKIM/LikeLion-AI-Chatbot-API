package com.minje.chatbot.service;

import com.minje.chatbot.dto.ChatRequest;
import com.minje.chatbot.dto.ChatResponse;
import com.minje.chatbot.dto.ConversationDTO;

import com.minje.chatbot.entity.Conversation;
import com.minje.chatbot.entity.Message;
import com.minje.chatbot.entity.User;
import com.minje.chatbot.exception.CustomException;
import com.minje.chatbot.repository.ConversationRepository;
import com.minje.chatbot.repository.MessageRepository;
import com.minje.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ChatService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OpenAIService openAIService;

    // 기본 사용자 ID (개발/테스트용)
    private static final Long DEFAULT_USER_ID = 1L;

    @Transactional
    public ChatResponse sendMessage(ChatRequest request) {
        // 기본 사용자 가져오기 (또는 자동 생성)
        User user = getOrCreateDefaultUser();

        // 대화 조회 또는 생성
        Conversation conversation = getOrCreateConversation(user.getId(), request.getConversationId());

        // 사용자 메시지 저장
        Message userMessage = saveMessage(conversation.getId(), Message.Role.USER, request.getContent());

        // 이전 대화 이력 가져오기 (최근 10개만)
        List<Message> conversationHistory = getRecentMessages(conversation.getId());

        // OpenAI API 호출
        String aiResponse = openAIService.createChatCompletion(
                conversationHistory,
                request.getContent()
        );

        // AI 응답 저장
        Message assistantMessage = saveMessage(conversation.getId(), Message.Role.ASSISTANT, aiResponse);

        // 대화 제목 설정 (첫 메시지인 경우)
        if (conversation.getTitle() == null || conversation.getTitle().isEmpty()) {
            String title = (request.getTitle() != null && !request.getTitle().isBlank())
                    ? request.getTitle()
                    : request.getContent();
            updateConversationTitle(conversation, title);
        }

        return ChatResponse.builder()
                .conversationId(conversation.getId())
                .userMessage(ChatResponse.MessageInfo.builder()
                        .id(userMessage.getId())
                        .role(Message.Role.USER.getValue())
                        .content(request.getContent())
                        .createdAt(userMessage.getCreatedAt())
                        .build())
                .assistantMessage(ChatResponse.MessageInfo.builder()
                        .id(assistantMessage.getId())
                        .role(Message.Role.ASSISTANT.getValue())
                        .content(aiResponse)
                        .createdAt(assistantMessage.getCreatedAt())
                        .build())
                .build();
    }

    @Transactional
    public SseEmitter sendMessageStream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5분 타임아웃

        // 기본 사용자 가져오기
        User user = getOrCreateDefaultUser();

        // 대화 조회 또는 생성
        Conversation conversation = getOrCreateConversation(user.getId(), request.getConversationId());

        // 사용자 메시지 저장
        saveMessage(conversation.getId(), Message.Role.USER, request.getContent());

        // 이전 대화 이력 (최근 10개만)
        List<Message> conversationHistory = getRecentMessages(conversation.getId());

        // 비동기 스트리밍
        new Thread(() -> {
            try {
                openAIService.createChatCompletionStream(
                        conversationHistory,
                        request.getContent(),
                        emitter
                );

                // 대화 제목 설정
                if (conversation.getTitle() == null || conversation.getTitle().isEmpty()) {
                    String title = (request.getTitle() != null && !request.getTitle().isBlank())
                            ? request.getTitle()
                            : request.getContent();
                    updateConversationTitle(conversation, title);
                }

            } catch (Exception e) {
                log.error("Error in streaming chat", e);
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @Transactional
    public Page<ConversationDTO> getConversations(Pageable pageable) {
        User user = getOrCreateDefaultUser();

        Page<Conversation> conversations = conversationRepository
                .findByUserId(user.getId(), pageable);

        return conversations.map(this::toConversationDTO);
    }

    public ConversationDTO getConversation(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException("NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND));

        List<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);

        return ConversationDTO.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .title(conversation.getTitle())
                .messageCount(messages.size())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException("NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND));

        conversationRepository.delete(conversation);
        log.info("Deleted conversation: {}", conversationId);
    }

    // === Private Helper Methods ===

    private User getOrCreateDefaultUser() {
        return userRepository.findByApiKey("default-user")
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .apiKey("default-user")
                            .build();
                    return userRepository.save(newUser);
                });
    }

    private Conversation getOrCreateConversation(Long userId, Long conversationId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new CustomException("NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND));
        }

        // 새 대화 생성
        Conversation newConversation = Conversation.builder()
                .userId(userId)
                .build();

        return conversationRepository.save(newConversation);
    }

    private List<Message> getRecentMessages(Long conversationId) {
        List<Message> messages = new ArrayList<>(
                messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId));
        Collections.reverse(messages);
        return messages;
    }

    private Message saveMessage(Long conversationId, Message.Role role, String content) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .build();

        return messageRepository.save(message);
    }

    @Transactional
    private void updateConversationTitle(Conversation conversation, String firstMessage) {
        String title = firstMessage.length() > 50
                ? firstMessage.substring(0, 50) + "..."
                : firstMessage;

        conversation.setTitle(title);
        conversationRepository.save(conversation);
    }

    private ConversationDTO toConversationDTO(Conversation conversation) {
        return ConversationDTO.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .title(conversation.getTitle())
                .messageCount(conversation.getMessageCount())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

}