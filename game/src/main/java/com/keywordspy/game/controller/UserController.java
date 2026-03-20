package com.keywordspy.game.controller;

import com.keywordspy.game.model.User;
import com.keywordspy.game.model.UserStats;
import com.keywordspy.game.repository.UserStatsRepository;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserStatsRepository userStatsRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // GET /users/me - Lấy profile + stats
    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        try {
            User user = getCurrentUser();
            UserStats stats = userStatsRepository.findByUserId(user.getId())
                    .orElse(new UserStats());

            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("total_games", stats.getTotalGames());
            statsMap.put("wins_civilian", stats.getWinsCivilian());
            statsMap.put("wins_spy", stats.getWinsSpy());
            statsMap.put("wins_infected", stats.getWinsInfected());
            statsMap.put("times_as_spy", stats.getTimesAsSpy());
            statsMap.put("times_infected", stats.getTimesInfected());
            statsMap.put("correct_votes", stats.getCorrectVotes());

            Map<String, Object> response = new HashMap<>();
            response.put("user_id", user.getId());
            response.put("username", user.getUsername());
            response.put("display_name", user.getDisplayName() != null ? user.getDisplayName() : "");
            response.put("email", user.getEmail());
            response.put("created_at", user.getCreatedAt().toString());
            
            // --- ECONOMY INFO ---
            response.put("balance", user.getBalance());
            response.put("ranking_points", user.getRankingPoints());
            // --------------------

            response.put("stats", statsMap);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // PUT /users/me - Cập nhật profile
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();

            if (request.containsKey("display_name")) {
                user.setDisplayName(request.get("display_name"));
            }

            userService.saveUser(user);

            return ResponseEntity.ok(Map.of(
                    "user_id", user.getId(),
                    "username", user.getUsername(),
                    "display_name", user.getDisplayName() != null ? user.getDisplayName() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}