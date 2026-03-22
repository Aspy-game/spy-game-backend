package com.keywordspy.game;

import com.keywordspy.game.ai.AIFallbackService;
import com.keywordspy.game.ai.AIPromptBuilder;
import com.keywordspy.game.ai.AIService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest
public class AIServiceTest {

    @Autowired
    private AIService aiService;

    @Test
    void testGenerateDescription() {
        String result = aiService.generateDescription("HOSPITAL");
        System.out.println("AI Description: " + result);
        assert result != null && !result.isEmpty();
    }

    @Test
    void testGenerateChatMessage() {
        Map<String, String> descriptions = Map.of(
            "player1", "Nơi này rất đông người mỗi ngày",
            "player2", "Bạn sẽ gặp nhiều người mặc đồ trắng ở đây"
        );
        String result = aiService.generateChatMessage("HOSPITAL", descriptions);
        System.out.println("AI Chat: " + result);
        assert result != null && !result.isEmpty();
    }

    @Test
    void testGenerateVoteTarget() {
        Map<String, String> descriptions = Map.of(
            "player1", "Nơi này rất đông người mỗi ngày",
            "player2", "Bạn sẽ gặp nhiều người mặc đồ trắng ở đây",
            "player3", "Tôi thích đi biển vào mùa hè"
        );
        String result = aiService.generateVoteTarget("HOSPITAL", descriptions, "ai_123");
        System.out.println("AI Vote Target: " + result);
        assert result != null;
    }
}