package com.minje.chatbot.service;

import com.minje.chatbot.dto.ChatRequest;
import com.minje.chatbot.dto.ChatResponse;
import com.minje.chatbot.dto.ConversationDTO;
import com.minje.chatbot.dto.MessageDTO;

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

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ChatService {

    private static final int MAX_SYSTEM_PROMPT_LENGTH = 1000;
    private static final int MAX_CONTENT_LENGTH = 5000;

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OpenAIService openAIService;
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(10);

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdown();
        try {
            if (!streamExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                streamExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            streamExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Transactional
    public ChatResponse sendMessage(String apiKey, ChatRequest request) {
        validateInput(request);
        User user = getUserByApiKey(apiKey);

        // 대화 조회 또는 생성
        Conversation conversation = getOrCreateConversation(user.getId(), request.getConversationId());

        // 기존 대화인 경우 소유권 검증
        if (request.getConversationId() != null) {
            validateOwnership(conversation, user.getId());
        }

        // 새 대화이고 systemPrompt가 있으면 SYSTEM 메시지로 저장
        if (request.getConversationId() == null && request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            saveMessage(conversation.getId(), Message.Role.SYSTEM, request.getSystemPrompt());
        }

        // 시스템 프롬프트 결정: 요청에 있으면 우선, 없으면 DB에서 조회
        String systemPrompt = resolveSystemPrompt(conversation.getId(), request.getSystemPrompt());

        // 사용자 메시지 저장
        Message userMessage = saveMessage(conversation.getId(), Message.Role.USER, request.getContent());

        // 이전 대화 이력 가져오기 (최근 10개만)
        List<Message> conversationHistory = getRecentMessages(conversation.getId());

        // OpenAI API 호출
        String aiResponse = openAIService.createChatCompletion(
                conversationHistory,
                request.getContent(),
                systemPrompt
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
    public SseEmitter sendMessageStream(String apiKey, ChatRequest request) {
        validateInput(request);
        SseEmitter emitter = new SseEmitter(300000L); // 5분 타임아웃

        User user = getUserByApiKey(apiKey);

        // 대화 조회 또는 생성
        Conversation conversation = getOrCreateConversation(user.getId(), request.getConversationId());

        // 기존 대화인 경우 소유권 검증
        if (request.getConversationId() != null) {
            validateOwnership(conversation, user.getId());
        }

        // 새 대화이고 systemPrompt가 있으면 SYSTEM 메시지로 저장
        if (request.getConversationId() == null && request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            saveMessage(conversation.getId(), Message.Role.SYSTEM, request.getSystemPrompt());
        }

        // 시스템 프롬프트 결정
        String systemPrompt = resolveSystemPrompt(conversation.getId(), request.getSystemPrompt());

        // 사용자 메시지 저장
        saveMessage(conversation.getId(), Message.Role.USER, request.getContent());

        // 이전 대화 이력 (최근 10개만)
        List<Message> conversationHistory = getRecentMessages(conversation.getId());

        // 비동기 스트리밍
        streamExecutor.submit(() -> {
            try {
                openAIService.createChatCompletionStream(
                        conversationHistory,
                        request.getContent(),
                        systemPrompt,
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
        });

        return emitter;
    }

    public Page<ConversationDTO> getConversations(String apiKey, Pageable pageable) {
        User user = getUserByApiKey(apiKey);

        Page<Conversation> conversations = conversationRepository
                .findByUserId(user.getId(), pageable);

        return conversations.map(this::toConversationDTO);
    }

    public ConversationDTO getConversation(String apiKey, Long conversationId) {
        User user = getUserByApiKey(apiKey);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException("NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND));

        validateOwnership(conversation, user.getId());

        List<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);

        List<MessageDTO> messageDTOs = messages.stream()
                .map(msg -> MessageDTO.builder()
                        .id(msg.getId())
                        .conversationId(msg.getConversationId())
                        .role(msg.getRole().getValue())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .toList();

        return ConversationDTO.builder()
                .id(conversation.getId())
                .userId(conversation.getUserId())
                .title(conversation.getTitle())
                .messageCount(messages.size())
                .messages(messageDTOs)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Transactional
    public void deleteConversation(String apiKey, Long conversationId) {
        User user = getUserByApiKey(apiKey);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException("NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND));

        validateOwnership(conversation, user.getId());

        messageRepository.deleteAllByConversationId(conversationId);
        conversationRepository.delete(conversation);
        log.info("Deleted conversation: {}", conversationId);
    }

    // === Private Helper Methods ===

    private void validateInput(ChatRequest request) {
        if (request.getContent() != null && request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new CustomException("BAD_REQUEST", "메시지는 " + MAX_CONTENT_LENGTH + "자 이하여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        if (request.getSystemPrompt() != null && request.getSystemPrompt().length() > MAX_SYSTEM_PROMPT_LENGTH) {
            throw new CustomException("BAD_REQUEST", "시스템 프롬프트는 " + MAX_SYSTEM_PROMPT_LENGTH + "자 이하여야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private User getUserByApiKey(String apiKey) {
        return userRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new CustomException("UNAUTHORIZED", "Invalid API Key", HttpStatus.UNAUTHORIZED));
    }

    private void validateOwnership(Conversation conversation, Long userId) {
        if (!conversation.getUserId().equals(userId)) {
            throw new CustomException("FORBIDDEN", "Access denied to this conversation", HttpStatus.FORBIDDEN);
        }
    }

    private String resolveSystemPrompt(Long conversationId, String requestSystemPrompt) {
        // 요청에 systemPrompt가 있으면 우선 사용
        if (requestSystemPrompt != null && !requestSystemPrompt.isBlank()) {
            return requestSystemPrompt;
        }
        // 없으면 DB에서 저장된 SYSTEM 메시지 조회
        return messageRepository.findFirstByConversationIdAndRoleOrderByCreatedAtAsc(conversationId, Message.Role.SYSTEM)
                .map(Message::getContent)
                .orElse(null);
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