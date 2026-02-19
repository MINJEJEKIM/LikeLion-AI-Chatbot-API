package com.minje.chatbot.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyValidator {

    private static final String API_KEY_PREFIX = "sk-proj-";
    private static final int API_KEY_LENGTH = 256;

    /**
     * API Key 생성
     */
    public String generateApiKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[API_KEY_LENGTH];
        random.nextBytes(bytes);

        String randomPart = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        return API_KEY_PREFIX + randomPart;
    }

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
