package com.keywordspy.game.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AIService {

    @Value("${groq.api-key}")
    private String apiKey;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final RestTemplate restTemplate = new RestTemplate();
    private final AIPromptBuilder promptBuilder;
    private final AIFallbackService fallback;

    public AIService(AIPromptBuilder promptBuilder, AIFallbackService fallback) {
        this.promptBuilder = promptBuilder;
        this.fallback = fallback;
    }

    public String generateDescription(String keyword) {
        try {
            return callGroq(promptBuilder.buildDescribePrompt(keyword));
        } catch (Exception e) {
            return fallback.getDescriptionFallback();
        }
    }

    public String generateChatMessage(String keyword, Map<String, String> descriptions) {
        try {
            return callGroq(promptBuilder.buildChatPrompt(keyword, descriptions));
        } catch (Exception e) {
            return fallback.getChatFallback();
        }
    }

    public String generateVoteTarget(String myKeyword, Map<String, String> descriptions, String myUserId) {
        try {
            String result = callGroq(promptBuilder.buildVotePrompt(myKeyword, descriptions, myUserId)).trim();
            return descriptions.containsKey(result) ? result : fallback.getRandomVoteTarget(descriptions, myUserId);
        } catch (Exception e) {
            return fallback.getRandomVoteTarget(descriptions, myUserId);
        }
    }

    @SuppressWarnings("unchecked")
    private String callGroq(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "model", "llama-3.3-70b-versatile",
            "max_tokens", 150,
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            )
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            GROQ_URL,
            new HttpEntity<>(body, headers),
            Map.class
        );

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return ((String) message.get("content")).trim();
    }
}