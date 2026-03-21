package com.keywordspy.game.controller;

import com.keywordspy.game.model.Match;
import com.keywordspy.game.model.KeywordPair;
import com.keywordspy.game.model.Role;
import com.keywordspy.game.model.GameSettings;
import com.keywordspy.game.model.Room;
import com.keywordspy.game.model.User;
import com.keywordspy.game.repository.KeywordPairRepository;
import com.keywordspy.game.repository.MatchRepository;
import com.keywordspy.game.service.RoomService;
import com.keywordspy.game.service.UserService;
import com.keywordspy.game.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private KeywordPairRepository keywordPairRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private SettingsService settingsService;
    // --- 1. Quản lý người dùng (User Management) ---
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll()); 
    }

    @GetMapping("/users/count")
    public ResponseEntity<Long> getUserCount() {
        return ResponseEntity.ok(userService.countUsers());
    }

    @PatchMapping("/users/{id}/ban")
    public ResponseEntity<User> banUser(@PathVariable String id, @RequestBody Map<String, Boolean> payload) {
        boolean active = payload.getOrDefault("active", true);
        return ResponseEntity.ok(userService.updateActiveStatus(id, active));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<User> updateRole(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String roleStr = payload.get("role");
        if (roleStr == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Role role = Role.valueOf(roleStr.toUpperCase());
            return ResponseEntity.ok(userService.updateRole(id, role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("new_password");
        if (newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body("New password is required");
        }
        userService.resetPassword(id, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    // --- 2. Quản lý game / room (Game & Room Management) ---
    @GetMapping("/rooms")
    public ResponseEntity<List<Room>> getAllRooms() {
        return ResponseEntity.ok(roomService.findAllRooms());
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable String id) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok(Map.of("message", "Room ended/deleted successfully"));
    }

    // --- 3. Theo dõi hệ thống (System Monitoring) ---
    @GetMapping("/stats")
    public ResponseEntity<?> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_users", userService.countUsers());
        stats.put("active_rooms", roomService.countActiveRooms());
        stats.put("total_matches", matchRepository.count());
        stats.put("total_keywords", keywordPairRepository.count());
        return ResponseEntity.ok(stats);
    }

    // --- 4. Quản lý nội dung (Content Management) ---
    @PostMapping("/keywords")
    public ResponseEntity<KeywordPair> addKeyword(@RequestBody KeywordPair keywordPair) {
        return ResponseEntity.ok(keywordPairRepository.save(keywordPair));
    }

    @GetMapping("/keywords")
    public ResponseEntity<List<KeywordPair>> getAllKeywords() {
        return ResponseEntity.ok(keywordPairRepository.findAll());
    }

    @DeleteMapping("/keywords/{id}")
    public ResponseEntity<?> deleteKeyword(@PathVariable String id) {
        keywordPairRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- 5. Cấu hình game (Admin only) ---
    @GetMapping("/settings")
    public ResponseEntity<GameSettings> getSettings() {
        return ResponseEntity.ok(settingsService.getOrDefault());
    }

    @PatchMapping("/settings")
    public ResponseEntity<GameSettings> updateSettings(@RequestBody Map<String, Object> payload) {
        GameSettings current = settingsService.getOrDefault();
        if (payload.containsKey("max_players")) {
            Object v = payload.get("max_players");
            if (v instanceof Number) current.setMaxPlayers(((Number) v).intValue());
        }
        if (payload.containsKey("min_players")) {
            Object v = payload.get("min_players");
            if (v instanceof Number) current.setMinPlayers(((Number) v).intValue());
        }
        if (payload.containsKey("spies_count")) {
            Object v = payload.get("spies_count");
            if (v instanceof Number) current.setSpiesCount(((Number) v).intValue());
        }
        if (payload.containsKey("describe_duration")) {
            Object v = payload.get("describe_duration");
            if (v instanceof Number) current.setDescribeDuration(((Number) v).intValue());
        }
        if (payload.containsKey("discuss_duration")) {
            Object v = payload.get("discuss_duration");
            if (v instanceof Number) current.setDiscussDuration(((Number) v).intValue());
        }
        if (payload.containsKey("vote_duration")) {
            Object v = payload.get("vote_duration");
            if (v instanceof Number) current.setVoteDuration(((Number) v).intValue());
        }
        if (payload.containsKey("role_check_duration")) {
            Object v = payload.get("role_check_duration");
            if (v instanceof Number) current.setRoleCheckDuration(((Number) v).intValue());
        }
        if (payload.containsKey("role_check_result_duration")) {
            Object v = payload.get("role_check_result_duration");
            if (v instanceof Number) current.setRoleCheckResultDuration(((Number) v).intValue());
        }
        return ResponseEntity.ok(settingsService.save(current));
    }
}
