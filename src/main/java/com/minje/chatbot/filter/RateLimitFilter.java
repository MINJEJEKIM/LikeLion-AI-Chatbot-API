package com.minje.chatbot.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minje.chatbot.dto.ApiResponse;
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
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_SECONDS = 60;

    private static final String LUA_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return count";

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
        String hashedKey = (String) request.getAttribute("apiKey");

        if (hashedKey == null) {
            writeErrorResponse(response, request);
            return;
        }

        String redisKey = "rate_limit:" + hashedKey;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long count = stringRedisTemplate.execute(script,
                Collections.singletonList(redisKey),
                String.valueOf(WINDOW_SECONDS));

        if (count == null) {
            count = 0L;
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

    private void writeErrorResponse(HttpServletResponse response,
                                    HttpServletRequest request) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<?> errorResponse = ApiResponse.error(
                ApiResponse.ErrorInfo.builder()
                        .code("UNAUTHORIZED")
                        .message("인증 정보가 없습니다.")
                        .timestamp(LocalDateTime.now().toString())
                        .path(request.getRequestURI())
                        .build()
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
