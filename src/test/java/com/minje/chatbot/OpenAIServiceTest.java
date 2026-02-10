package com.minje.chatbot;

import com.minje.chatbot.entity.Message;
import com.minje.chatbot.service.OpenAIService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "openai.api-key=${OPENAI_API_KEY}",
        "openai.model=gpt-3.5-turbo",
        "openai.max-tokens=100",
        "openai.temperature=0.7",
        "openai.timeout=30"
})
class OpenAIServiceTest {

    private OpenAIService openAIService;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens}")
    private Integer maxTokens;

    @Value("${openai.temperature}")
    private Double temperature;

    @Value("${openai.timeout}")
    private Integer timeout;

    @BeforeEach
    void setUp() {
        openAIService = new OpenAIService(apiKey, model, maxTokens, temperature, timeout);
    }

    @Test
    void testOpenAIConnection() {
        System.out.println("=== OpenAI API 연결 테스트 ===");

        assertNotNull(apiKey, "API Key가 null입니다.");
        assertFalse(apiKey.isEmpty(), "API Key가 비어있습니다.");

        System.out.println("API Key 설정 확인");
    }

    @Test
    void testSimpleChatCompletion() {
        System.out.println("=== 간단한 채팅 완료 테스트 ===");

        List<Message> conversationHistory = new ArrayList<>();
        String userMessage = "안녕하세요! 이것은 테스트 메시지입니다.";

        System.out.println("요청: " + userMessage);

        String response = openAIService.createChatCompletion(conversationHistory, userMessage);

        System.out.println("응답: " + response);

        assertNotNull(response, "응답이 null입니다.");
        assertFalse(response.isEmpty(), "응답이 비어있습니다.");
        assertTrue(response.length() > 0, "응답 길이가 0입니다.");

        System.out.println("✅ 채팅 완료 테스트 성공");
    }

    @Test
    void testChatWithConversationHistory() {
        System.out.println("=== 대화 이력 포함 테스트 ===");

        List<Message> conversationHistory = new ArrayList<>();

        // 이전 대화 추가
        Message msg1 = Message.builder()
                .role(Message.Role.USER)
                .content("내 이름은 민제야")
                .build();
        conversationHistory.add(msg1);

        Message msg2 = Message.builder()
                .role(Message.Role.ASSISTANT)
                .content("안녕하세요 민제님! 무엇을 도와드릴까요?")
                .build();
        conversationHistory.add(msg2);

        String userMessage = "내 이름이 뭐였지?";

        System.out.println("요청 (컨텍스트 포함): " + userMessage);

        String response = openAIService.createChatCompletion(conversationHistory, userMessage);

        System.out.println("응답: " + response);

        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("민제"), "응답에 '민제'가 포함되어야 합니다.");

        System.out.println("✅ 대화 이력 포함 테스트 성공");
    }

    @Test
    void testMultipleRequests() {
        System.out.println("=== 다중 요청 테스트 ===");

        String[] testMessages = {
                "1 + 1은?",
                "Spring Boot란?",
                "안녕!"
        };

        List<Message> conversationHistory = new ArrayList<>();

        for (String msg : testMessages) {
            System.out.println("요청 " + (conversationHistory.size() / 2 + 1) + ": " + msg);

            String response = openAIService.createChatCompletion(conversationHistory, msg);

            System.out.println("응답 " + (conversationHistory.size() / 2 + 1) + ": " + response);

            assertNotNull(response);
            assertFalse(response.isEmpty());

            // 대화 이력에 추가
            conversationHistory.add(Message.builder().role(Message.Role.USER).content(msg).build());
            conversationHistory.add(Message.builder().role(Message.Role.ASSISTANT).content(response).build());
        }

        System.out.println("✅ 다중 요청 테스트 성공");
    }
}