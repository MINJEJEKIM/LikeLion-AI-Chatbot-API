package com.minje.chatbot.controller;

import com.minje.chatbot.dto.ApiResponse;
import com.minje.chatbot.dto.ChatRequest;
import com.minje.chatbot.dto.ChatResponse;
import com.minje.chatbot.dto.ConversationDTO;
import com.minje.chatbot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "채팅 API")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat/completions")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "메시지 전송", description = "GPT에게 메시지를 전송하고 응답을 받습니다.")
    public ApiResponse<ChatResponse> sendMessage(
            @Parameter(description = "채팅 요청", required = true)
            @Valid @RequestBody ChatRequest request) {

        log.info("Received chat request: conversationId={}, content={}",
                request.getConversationId(), request.getContent());

        ChatResponse response = chatService.sendMessage(request);
        return ApiResponse.success(response);
    }

    @PostMapping(value = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "스트리밍 메시지 전송", description = "GPT에게 메시지를 전송하고 스트리밍 방식으로 응답을 받습니다.")
    // Swagger UI는 SSE 스트리밍을 제대로 렌더링하지 못해서 원시 이벤트가 그대로 나열되는 상황이 발생함.
    public SseEmitter sendMessageStream(
            @Parameter(description = "채팅 요청", required = true)
            @Valid @RequestBody ChatRequest request) {

        log.info("Received streaming chat request: conversationId={}, message={}",
                request.getConversationId(), request.getContent());

        return chatService.sendMessageStream(request);
    }

    @GetMapping("/conversations")
    @Operation(summary = "대화 목록 조회", description = "사용자의 모든 대화 목록을 조회합니다.")
    public ApiResponse<Page<ConversationDTO>> getConversations(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.info("Fetching conversations with pageable: {}", pageable);

        Page<ConversationDTO> conversations = chatService.getConversations(pageable);
        return ApiResponse.success(conversations);
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "특정 대화 조회", description = "특정 대화의 상세 정보를 조회합니다.")
    public ApiResponse<ConversationDTO> getConversation(
            @Parameter(description = "대화 ID", required = true)
            @PathVariable Long conversationId) {

        log.info("Fetching conversation: {}", conversationId);

        ConversationDTO conversation = chatService.getConversation(conversationId);
        return ApiResponse.success(conversation);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "대화 삭제", description = "특정 대화를 삭제합니다.")
    public ApiResponse<Void> deleteConversation(
            @Parameter(description = "대화 ID", required = true)
            @PathVariable Long conversationId) {

        log.info("Deleting conversation: {}", conversationId);

        chatService.deleteConversation(conversationId);
        return ApiResponse.success(null);
    }
}