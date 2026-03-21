package com.keywordspy.game.ai;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class AIFallbackService {

    private static final List<String> DESCRIBE_FALLBACKS = List.of(
        "Thứ này rất quen thuộc trong cuộc sống hàng ngày.",
        "Tôi thấy cái này ở khắp nơi xung quanh mình.",
        "Nhiều người dùng cái này mỗi ngày mà không để ý.",
        "Đây là thứ mà ai cũng biết nhưng ít nói đến.",
        "Cái này gắn liền với những khoảnh khắc đặc biệt."
    );

    private static final List<String> CHAT_FALLBACKS = List.of(
        "Tôi khá chắc về keyword của mình rồi.",
        "Câu mô tả của bạn nghe có vẻ mơ hồ quá.",
        "Mình nghĩ mọi người nên suy nghĩ kỹ hơn.",
        "Có ai thấy câu mô tả nào lạ không?"
    );

    private final Random random = new Random();

    public String getDescriptionFallback() {
        return DESCRIBE_FALLBACKS.get(random.nextInt(DESCRIBE_FALLBACKS.size()));
    }

    public String getChatFallback() {
        return CHAT_FALLBACKS.get(random.nextInt(CHAT_FALLBACKS.size()));
    }

    public String getRandomVoteTarget(Map<String, String> descriptions, String myUserId) {
        return descriptions.keySet().stream()
                .filter(id -> !id.equals(myUserId))
                .findAny()
                .orElse(null);
    }
}