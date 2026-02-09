package com.minje.chatbot.service;

import com.minje.chatbot.entity.Message;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OpenAIService {

    private final OpenAiService openAiService;
    private final String model;
    private final Integer maxTokens;
    private final Double temperature;

    public OpenAIService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model,
            @Value("${openai.max-tokens}") Integer maxTokens,
            @Value("${openai.temperature}") Double temperature,
            @Value("${openai.timeout}") Integer timeout) {
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(timeout));
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    /**
     * 일반 채팅 완료 (동기)
     */
    public String createChatCompletion(List<Message> conversationHistory, String userMessage) {
        try {
            List<ChatMessage> messages = convertToChatMessages(conversationHistory, userMessage);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();

            String response = openAiService.createChatCompletion(request)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            log.info("OpenAI response received: {} characters", response.length());
            return response;

        } catch (Exception e) {
            log.error("Error calling OpenAI API: ", e);
            throw new RuntimeException("OpenAI API 호출 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 스트리밍 채팅 완료
     */
    public void createChatCompletionStream(List<Message> conversationHistory,
                                           String userMessage,
                                           SseEmitter emitter) {
        try {
            List<ChatMessage> messages = convertToChatMessages(conversationHistory, userMessage);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .stream(true)
                    .build();

            Flowable<ChatCompletionChunk> flowable = openAiService.streamChatCompletion(request);

            StringBuilder fullResponse = new StringBuilder();

            flowable.doOnNext(chunk -> {
                        String content = chunk.getChoices().get(0).getMessage().getContent();
                        if (content != null) {
                            fullResponse.append(content);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("content")
                                        .data(content));
                            } catch (IOException e) {
                                log.error("Error sending SSE event: ", e);
                                throw new RuntimeException(e);
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data("[DONE]"));
                            emitter.complete();
                            log.info("Streaming completed. Total length: {}", fullResponse.length());
                        } catch (IOException e) {
                            log.error("Error completing SSE: ", e);
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(error -> {
                        log.error("Error during streaming: ", error);
                        emitter.completeWithError(error);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Error initializing OpenAI stream: ", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * Message 엔티티를 ChatMessage로 변환
     */
    private List<ChatMessage> convertToChatMessages(List<Message> conversationHistory, String userMessage) {
        List<ChatMessage> messages = conversationHistory.stream()
                .map(msg -> new ChatMessage(
                        msg.getRole().getValue(),
                        msg.getContent()
                ))
                .collect(Collectors.toList());

        // 현재 사용자 메시지 추가
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

        return messages;
    }
}