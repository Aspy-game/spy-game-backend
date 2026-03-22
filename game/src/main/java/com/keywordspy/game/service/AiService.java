package com.keywordspy.game.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    /**
     * Gọi Gemini API sử dụng Google GenAI SDK mới để lấy mô tả cho từ khóa.
     */
    public String getAiDescription(String keyword, int round) {
        String prompt = String.format(
            "Bạn là một người chơi trong trò chơi 'Keyword Spy'. Từ khóa của bạn là '%s'. " +
            "Đây là vòng chơi thứ %d. Hãy đưa ra một câu mô tả ngắn gọn (từ 1 đến 5 từ) về từ khóa này " +
            "sao cho những người cùng phe có thể hiểu nhưng gián điệp khó nhận ra. " +
            "Chỉ trả về nội dung mô tả, không thêm bất kỳ từ nào khác.",
            keyword, round
        );

        return callGeminiSdk(prompt, keyword);
    }

    public String askAi(String prompt) {
        return callGeminiSdk(prompt, "Hệ thống AI đang gặp sự cố.");
    }

    private String callGeminiSdk(String prompt, String fallback) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "AI: Vui lòng cấu hình Gemini API Key. (Prompt: " + prompt + ")";
        }

        try {
            System.out.println("[AI-SERVICE] Calling Gemini SDK (gemini-2.5-flash)...");
            
            // Khởi tạo client với API Key từ config
            Client client = Client.builder()
                    .apiKey(apiKey)
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    "gemini-2.5-flash", 
                    prompt, 
                    null
            );

            if (response != null && response.text() != null) {
                String result = response.text().trim();
                System.out.println("[AI-SERVICE] Gemini SDK Response: " + result);
                return result;
            }
        } catch (Exception e) {
            System.err.println("[AI-SERVICE-ERROR] SDK Exception: " + e.getMessage());
            // Tự động dùng fallback mô phỏng nếu SDK lỗi (hết hạn mức, v.v.)
            if (prompt.contains("Từ khóa của bạn là")) {
                return generateSimulatedDescription(fallback, 1);
            }
        }
        return fallback;
    }

    private String generateSimulatedDescription(String key, int round) {
        String[] templates = new String[]{
                "Liên tưởng đến " + key + " nhưng không trực tiếp",
                "Gợi nhớ một thứ gần với " + key,
                "Hơi hướng " + key + ", khá trừu tượng",
                "Nghĩ về chủ đề như " + key + " nhưng khác chữ",
                "Cảm giác tương tự " + key + " ở bối cảnh khác"
        };
        return templates[new Random().nextInt(templates.length)] + " (Dự phòng)";
    }
}
