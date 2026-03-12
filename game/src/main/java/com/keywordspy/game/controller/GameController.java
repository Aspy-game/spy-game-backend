package com.keywordspy.game.controller;

import com.keywordspy.game.model.GameSession;
import com.keywordspy.game.model.User;
import com.keywordspy.game.service.GameService;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.springframework.context.annotation.Profile;
import java.time.LocalDateTime;

import java.util.Map;

@RestController
@RequestMapping("/api")
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

    // T-018: POST /rooms/:roomId/start
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

    // GET /game/:matchId/state
    @GetMapping("/game/{matchId}/state")
    public ResponseEntity<?> getGameState(@PathVariable String matchId) {
        try {
            User user = getCurrentUser();
            Map<String, Object> state = gameService.getGameState(matchId, user.getId());
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // T-022: POST /game/:matchId/describe
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

    // T-025: POST /game/:matchId/chat
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

    // T-021: POST /game/:matchId/rolecheck
    @PostMapping("/game/{matchId}/rolecheck")
    public ResponseEntity<?> roleCheck(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            String guessedRole = request.get("guessed_role");
            boolean correct = gameService.checkRoleAndUnlockAbility(matchId, user.getId(), guessedRole);

            if (correct) {
                return ResponseEntity.ok(Map.of(
                        "correct", true,
                        "ability_available", "fake_message",
                        "message", "Năng lực đã được mở khóa!"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "correct", false,
                        "message", "Đoán sai rồi!"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // T-027: POST /game/:matchId/vote
    @PostMapping("/game/{matchId}/vote")
    public ResponseEntity<?> submitVote(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            String targetUserId = request.get("target_user_id");
            gameService.submitVote(matchId, user.getId(), targetUserId);

            return ResponseEntity.ok(Map.of("voted", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

      // DEBUG: Endpoint để thay đổi state của game thủ công
  // AN TOÀN: Chỉ hoạt động khi profile là "dev"
  @Profile("dev")
  @PostMapping("/game/{matchId}/set-state")
  public ResponseEntity<?> setGameState(
          @PathVariable String matchId,
          @RequestBody Map<String, String> request) {
      try {
          String stateStr = request.get("state");
          GameSession.GameState newState = GameSession.GameState.valueOf(stateStr.toUpperCase());
          gameService.setGameState(matchId, newState);
          return ResponseEntity.ok(Map.of("message", "Game state changed to " + newState));
      } catch (Exception e) {
          return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      }
  }
}