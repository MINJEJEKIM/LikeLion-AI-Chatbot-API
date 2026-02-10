package com.minje.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "채팅 응답 DTO")
public class ChatResponse {

    @Schema(description = "대화 세션 ID", example = "1")
    private Long conversationId;

    @Schema(description = "사용자 메시지")
    private MessageInfo userMessage;

    @Schema(description = "AI 응답 메시지")
    private MessageInfo assistantMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "메시지 정보")
    public static class MessageInfo {
        @Schema(description = "메시지 ID", example = "15")
        private Long id;

        @Schema(description = "역할", example = "user")
        private String role;

        @Schema(description = "메시지 내용", example = "안녕하세요!")
        private String content;

        @Schema(description = "생성 일시", example = "2024-02-02T10:05:00")
        private LocalDateTime createdAt;
    }
}