package com.keywordspy.game.controller;

import com.keywordspy.game.model.Match;
import com.keywordspy.game.model.User;
import com.keywordspy.game.service.GameService;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {

    @Autowired
    private GameService gameService;

    @Autowired
    private UserService userService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // API POST /api/game/{matchId}/rolecheck
    @PostMapping("/{matchId}/rolecheck")
    public ResponseEntity<?> roleCheck(@PathVariable String matchId, @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            String guessedRole = request.get("guessed_role");

            if (guessedRole == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "guessed_role is required"));
            }

            Match match = gameService.findMatchById(matchId)
                    .orElseThrow(() -> new RuntimeException("Match not found"));

            boolean correct = gameService.checkRoleAndUnlockAbility(match, user.getId(), guessedRole);

            if (correct) {
                return ResponseEntity.ok(Map.of(
                        "correct", true,
                        "ability_available", "fake_message",
                        "message", "Năng lực đã được mở khóa!"
                ));
            } else {
                // Kiểm tra xem tại sao sai (do không phải spy, vòng chưa tới, hay đoán sai)
                // Theo API specification thì chỉ cần trả về kết quả
                return ResponseEntity.ok(Map.of(
                        "correct", false,
                        "message", "Bạn chưa được phép mở năng lực hoặc đoán sai vai trò."
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
