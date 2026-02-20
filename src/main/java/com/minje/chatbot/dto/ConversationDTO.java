package com.minje.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "대화 세션 DTO")
public class ConversationDTO {

    @Schema(description = "대화 세션 ID", example = "1")
    private Long id;

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "대화 제목", example = "Spring Boot 학습")
    private String title;

    @Schema(description = "메시지 개수", example = "8")
    private Integer messageCount;

    @Schema(description = "메시지 목록 (상세 조회 시에만 포함)")
    private List<MessageDTO> messages;

    @Schema(description = "생성 일시", example = "2026-02-14T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시", example = "2026-02-14T14:30:00")
    private LocalDateTime updatedAt;
}