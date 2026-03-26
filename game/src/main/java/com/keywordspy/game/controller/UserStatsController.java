package com.keywordspy.game.controller;

import com.keywordspy.game.model.Match;
import com.keywordspy.game.model.User;
import com.keywordspy.game.model.UserStats;
import com.keywordspy.game.repository.MatchRepository;
import com.keywordspy.game.repository.UserStatsRepository;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class UserStatsController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserStatsRepository userStatsRepository;

    @Autowired
    private MatchRepository matchRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // GET /users/me/stats
    @GetMapping("/stats")
    public ResponseEntity<?> getMyStats() {
        try {
            User user = getCurrentUser();
            UserStats stats = userStatsRepository.findByUserId(user.getId())
                    .orElse(new UserStats());

            // Dùng số lượng thực tế từ MatchPlayer thay vì biến đếm totalGames
            long totalGamesPlayed = matchPlayerRepository.countByUserId(user.getId());

            int totalWins = stats.getWinsCivilian() + stats.getWinsSpy() + stats.getWinsInfected();
            double winRate = totalGamesPlayed > 0
                    ? Math.round((double) totalWins / totalGamesPlayed * 1000.0) / 10.0
                    : 0.0;

            Map<String, Object> result = new HashMap<>();
            result.put("user_id", user.getId());
            result.put("username", user.getUsername());
            result.put("total_games", totalGamesPlayed);
            result.put("total_wins", totalWins);
            result.put("win_rate", winRate);
            result.put("wins_civilian", stats.getWinsCivilian());
            result.put("wins_spy", stats.getWinsSpy());
            result.put("wins_infected", stats.getWinsInfected());
            result.put("times_as_spy", stats.getTimesAsSpy());
            result.put("times_infected", stats.getTimesInfected());
            result.put("correct_votes", stats.getCorrectVotes());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Autowired
    private com.keywordspy.game.repository.MatchPlayerRepository matchPlayerRepository;

    // GET /users/me/history
    @GetMapping("/history")
    public ResponseEntity<?> getMyHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            User user = getCurrentUser();

            // Lấy 20 trận đấu gần nhất của người chơi
            List<com.keywordspy.game.model.MatchPlayer> matchPlayers = matchPlayerRepository.findTop20ByUserIdOrderByIdDesc(user.getId());

            List<Map<String, Object>> historyList = new java.util.ArrayList<>();
            for (com.keywordspy.game.model.MatchPlayer mp : matchPlayers) {
                Map<String, Object> historyItem = new HashMap<>();
                System.out.println("[HISTORY-DEBUG] Match " + mp.getMatchId() + " isAfk=" + mp.getAfk());
                
                matchRepository.findById(mp.getMatchId() != null ? mp.getMatchId() : "").ifPresent(match -> {
                    
                    // Xác định role hiển thị
                    String displayRole = "civilian";
                    if (mp.getRole() != null && mp.getRole().toString().equalsIgnoreCase("spy")) {
                        displayRole = "spy";
                    } else if (mp.isInfected()) {
                        displayRole = "infected";
                    }
                    
                    historyItem.put("role", displayRole);
                    
                    // Xử lý AFK
                    if (mp.getAfk() != null && mp.getAfk()) {
                        historyItem.put("did_win", false);
                        historyItem.put("status", "AFK");
                    } else {
                        historyItem.put("did_win", mp.isDidWin());
                        historyItem.put("status", mp.isDidWin() ? "WIN" : "LOSE");
                    }

                    historyItem.put("started_at", match.getStartedAt());
                    historyItem.put("match_id", match.getId());
                    historyItem.put("winner", match.getWinnerRole() != null ? match.getWinnerRole().toString() : "unknown");

                    historyList.add(historyItem);
                });
            }

            Map<String, Object> result = new HashMap<>();
            result.put("matches", historyList);
            result.put("total", historyList.size());
            result.put("page", page);
            result.put("size", size);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}