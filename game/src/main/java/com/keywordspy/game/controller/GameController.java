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
            return ResponseEntity.ok(gameService.getGameState(matchId, user.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // SECTION 2: PHASE ACTIONS (Describe / Chat / Vote)
    // =========================================================

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

    // T-027: POST /game/:matchId/vote
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
    // SECTION 3: SPY ABILITIES
    // =========================================================

    // T-021: POST /game/:matchId/rolecheck
    @PostMapping("/game/{matchId}/rolecheck")
    public ResponseEntity<?> roleCheck(
            @PathVariable String matchId,
            @RequestBody Map<String, String> request) {
        try {
            User user = getCurrentUser();
            Map<String, Object> result = gameService.checkRoleAndUnlockAbility(
                    matchId, user.getId(), request.get("guessed_role"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // T-023: POST /game/:matchId/ability/fake-message
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

    // T-024: POST /game/:matchId/ability/infect
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
    // DEBUG ONLY (chỉ hoạt động ở profile dev)
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
}