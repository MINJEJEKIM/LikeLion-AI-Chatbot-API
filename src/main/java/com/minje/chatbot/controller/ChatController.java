package com.minje.chatbot.controller;

import com.minje.chatbot.dto.ApiResponse;
import com.minje.chatbot.dto.ChatRequest;
import com.minje.chatbot.dto.ChatResponse;
import com.minje.chatbot.dto.ConversationDTO;
import com.minje.chatbot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    @Operation(
            summary = "메시지 전송",
            description = """
                    GPT에게 메시지를 전송하고 동기 방식으로 응답을 받습니다.
                    - `conversationId`가 null이면 새 대화 세션을 생성합니다.
                    - `conversationId`를 지정하면 기존 대화에 이어서 메시지를 전송합니다.
                    - `systemPrompt`를 지정하면 AI의 역할/페르소나를 설정할 수 있습니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "메시지 전송 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "conversationId": 1,
                                "userMessage": {
                                  "id": 10, "role": "user",
                                  "content": "안녕하세요!", "createdAt": "2026-02-23T10:00:00"
                                },
                                "assistantMessage": {
                                  "id": 11, "role": "assistant",
                                  "content": "안녕하세요! 무엇을 도와드릴까요?", "createdAt": "2026-02-23T10:00:01"
                                }
                              },
                              "error": null
                            }"""))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 검증 실패 (content 누락 등)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": {"content": "메시지 내용은 필수입니다."},
                              "error": {"code": "VALIDATION_ERROR", "message": "입력 값 검증에 실패했습니다.", "timestamp": "2026-02-23T10:00:00", "path": "/api/v1/chat/completions"}
                            }"""))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "API Key 누락 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false, "data": null,
                              "error": {"code": "UNAUTHORIZED", "message": "유효하지 않은 API Key입니다.", "timestamp": "2026-02-23T10:00:00", "path": "/api/v1/chat/completions"}
                            }"""))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false, "data": null,
                              "error": {"code": "INTERNAL_SERVER_ERROR", "message": "서버 내부 오류가 발생했습니다.", "timestamp": "2026-02-23T10:00:00", "path": "/api/v1/chat/completions"}
                            }"""))
            )
    })
    public ApiResponse<ChatResponse> sendMessage(
            @Parameter(description = "채팅 요청", required = true)
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {

        String apiKey = (String) httpRequest.getAttribute("apiKey");
        log.info("Received chat request: conversationId={}, content={}",
                request.getConversationId(), request.getContent());

        ChatResponse response = chatService.sendMessage(apiKey, request);
        return ApiResponse.success(response);
    }

    @PostMapping(value = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "스트리밍 메시지 전송",
            description = """
                    GPT에게 메시지를 전송하고 SSE(Server-Sent Events) 스트리밍 방식으로 응답을 받습니다.
                    - 응답은 `text/event-stream` 형식으로 전달됩니다.
                    - 각 이벤트의 `data` 필드에 토큰 단위의 응답이 포함됩니다.
                    - 스트림 종료 시 `[DONE]` 이벤트가 전송됩니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "스트리밍 응답 시작",
                    content = @Content(mediaType = "text/event-stream", examples = @ExampleObject(value = """
                            data: {"token": "안녕"}
                            data: {"token": "하세요"}
                            data: {"token": "!"}
                            data: [DONE]"""))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 검증 실패",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "API Key 누락 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json")
            )
    })
    public SseEmitter sendMessageStream(
            @Parameter(description = "채팅 요청", required = true)
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {

        String apiKey = (String) httpRequest.getAttribute("apiKey");
        log.info("Received streaming chat request: conversationId={}, message={}",
                request.getConversationId(), request.getContent());

        return chatService.sendMessageStream(apiKey, request);
    }

    @GetMapping("/conversations")
    @Operation(
            summary = "대화 목록 조회",
            description = """
                    인증된 사용자의 모든 대화 목록을 페이지네이션으로 조회합니다.
                    - 기본 페이지 크기: 20건
                    - 정렬 기준: 생성일시 내림차순
                    - `page`, `size`, `sort` 쿼리 파라미터로 페이징을 제어할 수 있습니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "대화 목록 조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "content": [
                                  {"id": 1, "userId": 1, "title": "Spring Boot 학습", "messageCount": 8, "messages": null, "createdAt": "2026-02-23T10:00:00", "updatedAt": "2026-02-23T14:30:00"},
                                  {"id": 2, "userId": 1, "title": "Java 질문", "messageCount": 4, "messages": null, "createdAt": "2026-02-22T09:00:00", "updatedAt": "2026-02-22T09:30:00"}
                                ],
                                "totalElements": 2, "totalPages": 1, "size": 20, "number": 0
                              },
                              "error": null
                            }"""))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "API Key 누락 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ApiResponse<Page<ConversationDTO>> getConversations(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            HttpServletRequest httpRequest) {

        String apiKey = (String) httpRequest.getAttribute("apiKey");
        log.info("Fetching conversations with pageable: {}", pageable);

        Page<ConversationDTO> conversations = chatService.getConversations(apiKey, pageable);
        return ApiResponse.success(conversations);
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(
            summary = "특정 대화 조회",
            description = """
                    대화 ID를 기반으로 특정 대화의 상세 정보를 조회합니다.
                    - 대화 세션 정보와 함께 해당 대화에 포함된 모든 메시지 목록이 반환됩니다.
                    - 다른 사용자의 대화는 조회할 수 없습니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "대화 조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "id": 1, "userId": 1, "title": "Spring Boot 학습", "messageCount": 2,
                                "messages": [
                                  {"id": 10, "conversationId": 1, "role": "user", "content": "Spring Boot란?", "createdAt": "2026-02-23T10:00:00"},
                                  {"id": 11, "conversationId": 1, "role": "assistant", "content": "Spring Boot는 스프링 프레임워크를 쉽게 사용할 수 있게 해주는 도구입니다.", "createdAt": "2026-02-23T10:00:01"}
                                ],
                                "createdAt": "2026-02-23T10:00:00", "updatedAt": "2026-02-23T10:00:01"
                              },
                              "error": null
                            }"""))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "API Key 누락 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "대화를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false, "data": null,
                              "error": {"code": "NOT_FOUND", "message": "대화를 찾을 수 없습니다.", "timestamp": "2026-02-23T10:00:00", "path": "/api/v1/conversations/999"}
                            }"""))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ApiResponse<ConversationDTO> getConversation(
            @Parameter(description = "대화 ID", required = true)
            @PathVariable Long conversationId,
            HttpServletRequest httpRequest) {

        String apiKey = (String) httpRequest.getAttribute("apiKey");
        log.info("Fetching conversation: {}", conversationId);

        ConversationDTO conversation = chatService.getConversation(apiKey, conversationId);
        return ApiResponse.success(conversation);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "대화 삭제",
            description = """
                    대화 ID를 기반으로 특정 대화를 삭제합니다.
                    - 해당 대화에 포함된 모든 메시지도 함께 삭제됩니다.
                    - 다른 사용자의 대화는 삭제할 수 없습니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "대화 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "API Key 누락 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "대화를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false, "data": null,
                              "error": {"code": "NOT_FOUND", "message": "대화를 찾을 수 없습니다.", "timestamp": "2026-02-23T10:00:00", "path": "/api/v1/conversations/999"}
                            }"""))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ApiResponse<Void> deleteConversation(
            @Parameter(description = "대화 ID", required = true)
            @PathVariable Long conversationId,
            HttpServletRequest httpRequest) {

        String apiKey = (String) httpRequest.getAttribute("apiKey");
        log.info("Deleting conversation: {}", conversationId);

        chatService.deleteConversation(apiKey, conversationId);
        return ApiResponse.success(null);
    }
}