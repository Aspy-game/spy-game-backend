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

    // T-051: Xem thống kê cá nhân
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
            result.put("userId", user.getId());
            result.put("username", user.getUsername());
            result.put("totalGames", stats.getTotalGames());
            result.put("totalWins", totalWins);
            result.put("winRate", winRate);
            result.put("winsCivilian", stats.getWinsCivilian());
            result.put("winsSpy", stats.getWinsSpy());
            result.put("winsInfected", stats.getWinsInfected());
            result.put("timesAsSpy", stats.getTimesAsSpy());
            result.put("timesInfected", stats.getTimesInfected());
            result.put("correctVotes", stats.getCorrectVotes());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // T-052: Xem lịch sử ván đấu
    @GetMapping("/history")
    public ResponseEntity<?> getMyHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            User user = getCurrentUser();

            List<Match> matches = matchRepository.findAll(PageRequest.of(page, size))
                    .stream()
                    .filter(m -> m.getSpyUserId() != null &&
                            (m.getSpyUserId().equals(user.getId()) ||
                             m.getRoomId() != null))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "page", page,
                    "size", size,
                    "matches", matches
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}