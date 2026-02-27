package com.minje.chatbot.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minje.chatbot.dto.ApiResponse;
import com.minje.chatbot.entity.User;
import com.minje.chatbot.repository.UserRepository;
import com.minje.chatbot.util.ApiKeyHashUtil;
import com.minje.chatbot.util.ApiKeyValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final int MAX_REGISTRATIONS_PER_HOUR = 5;
    private static final long WINDOW_SECONDS = 3600;

    private static final String LUA_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return count";

    private final ApiKeyValidator apiKeyValidator;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ApiKeyHashUtil apiKeyHashUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.endsWith("/health") ||
                path.contains("/swagger-ui") ||
                path.contains("/api-docs") ||
                path.contains("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isEmpty()) {
            writeErrorResponse(response, request, "API Key가 필요합니다.");
            return;
        }

        if (!apiKeyValidator.isValidFormat(apiKey)) {
            writeErrorResponse(response, request, "유효하지 않은 API Key 형식입니다.");
            return;
        }

        String hashedKey = apiKeyHashUtil.hash(apiKey);

        if (userRepository.findByApiKey(hashedKey).isEmpty()) {
            // IP 기반 자동 등록 횟수 제한 (Lua 스크립트로 원자적 처리)
            String clientIp = request.getRemoteAddr();
            String redisKey = "reg_limit:" + clientIp;
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
            Long count = stringRedisTemplate.execute(script,
                    Collections.singletonList(redisKey),
                    String.valueOf(WINDOW_SECONDS));

            if (count != null && count > MAX_REGISTRATIONS_PER_HOUR) {
                writeErrorResponse(response, request, "자동 등록 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
                return;
            }

            userRepository.save(User.builder().apiKey(hashedKey).build());
        }

        request.setAttribute("apiKey", hashedKey);
        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response,
                                    HttpServletRequest request,
                                    String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<?> errorResponse = ApiResponse.error(
                ApiResponse.ErrorInfo.builder()
                        .code("UNAUTHORIZED")
                        .message(message)
                        .timestamp(LocalDateTime.now().toString())
                        .path(request.getRequestURI())
                        .build()
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
