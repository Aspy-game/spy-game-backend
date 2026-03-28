package com.keywordspy.game.controller;

import com.keywordspy.game.service.SkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shop")
public class ShopController {

    @Autowired
    private SkillService skillService;

    @PostMapping("/buy")
    public ResponseEntity<?> buySkill(@RequestParam String skillId, @AuthenticationPrincipal String userId) {
        try {
            skillService.buySkill(userId, skillId);
            return ResponseEntity.ok(Map.of("message", "Mua kỹ năng thành công", "skill_id", skillId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/inventory")
    public ResponseEntity<?> getInventory(@AuthenticationPrincipal String userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not authenticated. Please log in."));
        }
        try {
            return ResponseEntity.ok(skillService.getInventory(userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
