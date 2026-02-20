package com.minje.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "채팅 요청 DTO")
public class ChatRequest {

    @NotBlank(message = "메시지 내용은 필수입니다.")
    @Schema(description = "사용자 메시지 내용", example = "안녕하세요! Spring Boot에 대해 알려주세요.")
    private String content;

    @Schema(description = "대화 세션 ID (신규 대화인 경우 null)", nullable = true)
    private Long conversationId;

    @Schema(description = "대화 제목 (신규 대화인 경우)", example = "Spring Boot 학습")
    private String title;

    @Schema(description = "시스템 프롬프트 (AI 역할/페르소나 지정, 선택값)", example = "너는 영어 튜터야", nullable = true)
    private String systemPrompt;
}