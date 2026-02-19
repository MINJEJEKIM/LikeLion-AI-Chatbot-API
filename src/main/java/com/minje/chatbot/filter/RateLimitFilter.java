package com.minje.chatbot.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minje.chatbot.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_SECONDS = 60;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/swagger-ui") ||
                path.contains("/api-docs") ||
                path.contains("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        // API Key가 없으면 ApiKeyAuthFilter에서 처리하므로 통과
        if (apiKey == null || apiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String redisKey = "rate_limit:" + apiKey;
        Long count = stringRedisTemplate.opsForValue().increment(redisKey);

        if (count == 1) {
            stringRedisTemplate.expire(redisKey, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, MAX_REQUESTS - count)));

        if (count > MAX_REQUESTS) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ApiResponse<?> errorResponse = ApiResponse.error(
                    ApiResponse.ErrorInfo.builder()
                            .code("TOO_MANY_REQUESTS")
                            .message("요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.")
                            .timestamp(LocalDateTime.now().toString())
                            .path(request.getRequestURI())
                            .build()
            );

            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
