package com.minje.chatbot.util;

import org.springframework.stereotype.Component;

@Component
public class ApiKeyValidator {

    private static final String API_KEY_PREFIX = "sk-";

    /**
     * API Key 형식 검증
     */
    public boolean isValidFormat(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        return apiKey.startsWith(API_KEY_PREFIX) &&
                apiKey.length() > API_KEY_PREFIX.length();
    }
}
