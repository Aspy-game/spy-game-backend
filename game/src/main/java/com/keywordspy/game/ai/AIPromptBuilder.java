package com.keywordspy.game.ai;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class AIPromptBuilder {

    public String buildDescribePrompt(String keyword) {
        return """
            You are playing a word-guessing party game called Spy Game.
            Your secret keyword is: "%s"

            Write EXACTLY ONE sentence (5-20 words) hinting at your keyword WITHOUT saying it directly.
            Rules:
            - Do NOT use the keyword or obvious synonyms
            - Sound natural, like a real Vietnamese person playing a game
            - Reply with ONLY the sentence, nothing else, no quotes
            """.formatted(keyword);
    }

    public String buildChatPrompt(String keyword, Map<String, String> descriptions) {
        StringBuilder sb = new StringBuilder();
        descriptions.forEach((userId, desc) -> sb.append("- ").append(desc).append("\n"));

        return """
            You are playing Spy Game. Your keyword is: "%s"

            Other players wrote these descriptions:
            %s

            Write ONE short discussion message (max 15 words) in Vietnamese.
            Sound casual. Reply ONLY with the message, nothing else.
            """.formatted(keyword, sb);
    }

    public String buildVotePrompt(String myKeyword, Map<String, String> descriptions, String myUserId) {
        StringBuilder sb = new StringBuilder();
        descriptions.forEach((userId, desc) -> {
            if (!userId.equals(myUserId))
                sb.append("playerId: ").append(userId).append(", description: ").append(desc).append("\n");
        });

        return """
            You are playing Spy Game. Your keyword is: "%s"

            Players and their descriptions:
            %s

            Which playerId seems most suspicious (likely the Spy)?
            Reply ONLY with the playerId string, nothing else.
            """.formatted(myKeyword, sb);
    }
}