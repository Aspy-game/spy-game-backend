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

            int totalWins = stats.getWinsCivilian() + stats.getWinsSpy() + stats.getWinsInfected();
            double winRate = stats.getTotalGames() > 0
                    ? Math.round((double) totalWins / stats.getTotalGames() * 1000.0) / 10.0
                    : 0.0;

            Map<String, Object> result = new HashMap<>();
            result.put("user_id", user.getId());
            result.put("username", user.getUsername());
            result.put("total_games", stats.getTotalGames());
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

    // GET /users/me/history
    @GetMapping("/history")
    public ResponseEntity<?> getMyHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            User user = getCurrentUser();

            List<Match> matches = matchRepository.findAll(PageRequest.of(page, size))
                    .stream()
                    .filter(m -> m.getSpyUserId() != null &&
                            m.getSpyUserId().equals(user.getId()))
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("matches", matches);
            result.put("total", matches.size());
            result.put("page", page);
            result.put("size", size);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}