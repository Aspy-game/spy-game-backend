package com.keywordspy.game.controller;

import com.keywordspy.game.model.User;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserService userService;

    // Example: Get all users
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        // This would require a findAll method in UserService/UserRepository
        return ResponseEntity.ok().build(); 
    }

    // Example: Monitor active sessions
    @GetMapping("/sessions")
    public ResponseEntity<?> getActiveSessions() {
        // Logic to get active game sessions
        return ResponseEntity.ok(Map.of("active_sessions", 0));
    }

    // Example: Add new keyword
    @PostMapping("/keywords")
    public ResponseEntity<?> addKeyword(@RequestBody Map<String, String> payload) {
        // Logic to add keywords
        return ResponseEntity.ok(Map.of("message", "Keyword added"));
    }
}
