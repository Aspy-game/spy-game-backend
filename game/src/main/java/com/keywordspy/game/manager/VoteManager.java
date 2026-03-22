package com.keywordspy.game.manager;

import com.keywordspy.game.model.GameSession;
import com.keywordspy.game.model.Player;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class VoteManager {

    // Xử lý vote và trả về userId bị loại (null nếu hòa)
    public String processVotes(GameSession session) {
        Map<String, String> votes = session.getCurrentRoundVotes();

        // Đếm số vote cho mỗi người
        Map<String, Integer> voteCounts = new HashMap<>();
        for (String targetId : votes.values()) {
            voteCounts.merge(targetId, 1, Integer::sum);
        }

        if (voteCounts.isEmpty()) return null;

        // Tìm số vote cao nhất
        int maxVotes = Collections.max(voteCounts.values());

        // Tìm những người có số vote cao nhất
        List<String> topVoted = voteCounts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();

        // Nếu chỉ có 1 người → bị loại
        if (topVoted.size() == 1) {
            return topVoted.get(0);
        }

        // Hòa → return null (cần Sudden Death)
        return null;
    }

    // Lấy danh sách người hòa phiếu
    public List<String> getTiedPlayers(GameSession session) {
        Map<String, String> votes = session.getCurrentRoundVotes();
        Map<String, Integer> voteCounts = new HashMap<>();

        for (String targetId : votes.values()) {
            voteCounts.merge(targetId, 1, Integer::sum);
        }

        if (voteCounts.isEmpty()) return new ArrayList<>();

        int maxVotes = Collections.max(voteCounts.values());

        return voteCounts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();
    }

    // Lấy vote count hiện tại (ẩn danh - chỉ trả về số lượng)
    public Map<String, Integer> getVoteCounts(GameSession session) {
        Map<String, String> votes = session.getCurrentRoundVotes();
        Map<String, Integer> voteCounts = new HashMap<>();

        for (Player player : session.getAlivePlayers()) {
            voteCounts.put(player.getUserId(), 0);
        }

        for (String targetId : votes.values()) {
            voteCounts.merge(targetId, 1, Integer::sum);
        }

        return voteCounts;
    }

    // Kiểm tra tất cả player còn sống đã vote chưa (AI không vote)
    public boolean allVoted(GameSession session) {
        Map<String, String> votes = session.getCurrentRoundVotes();
        long humanAliveCount = session.getAlivePlayers().stream()
                .filter(p -> !p.isAi())
                .count();
        return votes.size() >= humanAliveCount;
    }
}