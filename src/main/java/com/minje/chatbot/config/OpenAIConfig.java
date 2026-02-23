package com.minje.chatbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAIConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        String description = """
                ## 서비스 설명
                Spring Boot 기반 AI 챗봇 REST API입니다.
                OpenAI GPT 모델을 활용하여 대화형 AI 서비스를 제공하며, 대화 세션 관리(생성, 조회, 삭제) 기능을 포함합니다.

                ## 인증 방식
                모든 API 요청에는 `X-API-KEY` 헤더가 필요합니다.

                | 항목 | 값 |
                |------|-----|
                | **헤더 이름** | `X-API-KEY` |
                | **위치** | HTTP Header |
                | **필수 여부** | 필수 |
                | **형식** | 관리자로부터 발급받은 API Key 문자열 |

                ```
                curl -H "X-API-KEY: your-api-key" https://api-host/api/v1/conversations
                ```

                > **Rate Limit**: 60초당 최대 10회 요청 가능 (초과 시 429 응답)

                ## 에러 응답 형식
                모든 에러는 아래 공통 형식으로 반환됩니다.
                ```json
                {
                  "success": false,
                  "data": null,
                  "error": {
                    "code": "ERROR_CODE",
                    "message": "에러 메시지",
                    "timestamp": "2026-02-23T10:00:00",
                    "path": "/api/v1/..."
                  }
                }
                ```

                | HTTP 상태 코드 | 에러 코드 | 설명 |
                |---------------|----------|------|
                | **400** Bad Request | `VALIDATION_ERROR` | 요청 파라미터 검증 실패 (필수 값 누락, 형식 오류 등) |
                | **401** Unauthorized | `UNAUTHORIZED` | API Key 누락 또는 유효하지 않은 API Key |
                | **404** Not Found | `NOT_FOUND` | 요청한 리소스를 찾을 수 없음 (대화 세션 등) |
                | **429** Too Many Requests | `RATE_LIMIT_EXCEEDED` | 요청 횟수 초과 (60초당 10회 제한) |
                | **500** Internal Server Error | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |
                """;

        return new OpenAPI()
                .info(new Info()
                        .title("AI Chatbot REST API")
                        .version("1.0.0")
                        .description(description)
                        .contact(new Contact()
                                .name("MinJe")
                                .url("https://github.com/MINJEJEKIM/LikeLion-AI-Chatbot-API")
                                .email("rlaghrb123@gmail.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort + "/api/v1")
                                .description("Development Server")
                ));
    }
}