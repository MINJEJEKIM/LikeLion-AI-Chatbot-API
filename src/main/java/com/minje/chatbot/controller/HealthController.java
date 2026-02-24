package com.minje.chatbot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Health", description = "헬스체크 API")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "헬스체크", description = "서버 상태를 확인합니다.")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
