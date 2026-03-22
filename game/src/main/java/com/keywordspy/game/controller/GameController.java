package com.keywordspy.game.controller;

import com.keywordspy.game.model.GameSession;
import com.keywordspy.game.model.User;
import com.keywordspy.game.service.GameService;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GameController {

    @Autowired private GameService gameService;
    @Autowired private UserService userService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // =========================================================
    // SECTION 1: GAME SETUP
    // =========================================================

    @PostMapping("/rooms/{roomId}/start")
    public ResponseEntity<?> startGame(@PathVariable String roomId) {
        try {
            User user = getCurrentUser();
            GameSession session = gameService.startGame(roomId, user.getId());
            return ResponseEntity.ok(Map.of(
                    "match_id", session.getMatchId(),
                    "message", "Game started"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/rooms/{roomId}/admin/set-spy")
    public ResponseEntity<?> adminSetSpy(
            @PathVariable String roomId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            gameService.adminSetSpy(roomId, user.getId(), request.get("user_id"));
            return ResponseEntity.ok(Map.of("message", "Spy set successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/game/{matchId}/state")
    public ResponseEntity<?> getGameState(@PathVariable String matchId) {
        try {
            User user = getCurrentUser();
            return ResponseEntity.ok(gameService.getGameState(matchId, user.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // SECTION 2: PHASE ACTIONS (Describe / Chat / Vote)
    // =========================================================

    @PostMapping("/game/{matchId}/describe")
    public ResponseEntity<?> submitDescription(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            String content = request.get("content");
            gameService.submitDescription(matchId, user.getId(), content);
            return ResponseEntity.ok(Map.of(
                    "submitted", true,
                    "word_count", content.trim().split("\\s+").length
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/game/{matchId}/chat")
    public ResponseEntity<?> submitChat(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            gameService.submitChat(matchId, user.getId(), request.get("content"));
            return ResponseEntity.ok(Map.of("submitted", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/game/{matchId}/vote")
    public ResponseEntity<?> submitVote(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            gameService.submitVote(matchId, user.getId(), request.get("target_user_id"));
            return ResponseEntity.ok(Map.of("voted", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // SECTION 3: VÒNG ĐOÁN VAI TRÒ
    // =========================================================

    /**
     * POST /game/:matchId/rolecheck
     * Tất cả 6 người đều gọi endpoint này trong phase ROLE_CHECK (20s đoán).
     * Body: { "guessed_role": "spy" } hoặc { "guessed_role": "civilian" }
     * Response: { "submitted": true }
     * Kết quả thực sự được gửi qua WebSocket /user/queue/role-check-result
     */
    @PostMapping("/game/{matchId}/rolecheck")
    public ResponseEntity<?> submitRoleGuess(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            Map<String, Object> result = gameService.submitRoleGuess(
                    matchId, user.getId(), request.get("guessed_role"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /game/:matchId/rolecheck/confirm-ability
     * Chỉ Spy gọi trong phase ROLE_CHECK_RESULT (20s kết quả).
     * Body: { "ability_type": "fake_message" / "infection" / "none" }
     * Response: { "confirmed": true, "ability": "..." }
     */
    @PostMapping("/game/{matchId}/rolecheck/confirm-ability")
    public ResponseEntity<?> confirmSpyAbility(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            String abilityType = request.get("ability_type");
            Map<String, Object> result = gameService.confirmSpyAbility(
                    matchId, user.getId(), abilityType);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // SECTION 4: SPY ABILITIES
    // =========================================================

    /**
     * POST /game/:matchId/ability/fake-message
     * Spy dùng trong DESCRIBING hoặc DISCUSSING, mỗi vòng 1 lần.
     * Body: { "content": "nội dung giả mạo 5-20 từ" }
     */
    @PostMapping("/game/{matchId}/ability/fake-message")
    public ResponseEntity<?> useFakeMessage(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            Map<String, Object> result = gameService.useFakeMessageAbility(
                    matchId, user.getId(), request.get("content"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /game/:matchId/ability/infect
     * Spy dùng trong ROLE_CHECK_RESULT (20s kết quả), sau khi confirm-ability = infection.
     * Body: { "target_user_id": "userId" }
     */
    @PostMapping("/game/{matchId}/ability/infect")
    public ResponseEntity<?> infectPlayer(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            Map<String, Object> result = gameService.infectPlayer(
                    matchId, user.getId(), request.get("target_user_id"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // DEBUG ONLY
    // =========================================================

    @Profile("dev")
    @PostMapping("/game/{matchId}/set-state")
    public ResponseEntity<?> setGameState(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            GameSession.GameState newState = GameSession.GameState.valueOf(
                    request.get("state").toUpperCase());
            gameService.setGameState(matchId, newState);
            return ResponseEntity.ok(Map.of("message", "State changed to " + newState));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/game/{matchId}/admin/adjust-rewards")
    public ResponseEntity<?> adjustRewards(
            @PathVariable String matchId,
            @RequestBody Map<String, Integer> request) {
        try {
            User user = getCurrentUser();
            gameService.adjustRewards(matchId, user.getId(), 
                    request.get("civilian"), request.get("spy"), request.get("infected"));
            return ResponseEntity.ok(Map.of("message", "Rewards adjusted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Profile("dev")
    @PostMapping("/game/{matchId}/set-spy")
    public ResponseEntity<?> setGameSpy(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            gameService.setGameSpyDebug(matchId, request.get("user_id"));
            return ResponseEntity.ok(Map.of("message", "Spy changed to " + request.get("user_id")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}