package com.keywordspy.game.controller;

import com.keywordspy.game.service.GameService;
import com.keywordspy.game.service.SkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/skill")
public class SkillController {

    @Autowired
    private SkillService skillService;

    @Autowired
    private GameService gameService;

    /**
     * Sử dụng kỹ năng "Vòng Đặc Biệt" cho phòng chơi (Chủ phòng sử dụng)
     * Tiêu tốn 500 xu của người sử dụng (thường là chủ phòng)
     */
    @PostMapping("/special-round")
    public ResponseEntity<?> useSpecialRound(@RequestParam String roomId, @AuthenticationPrincipal String userId) {
        try {
            // Kiểm tra và tiêu xu của người sử dụng
            skillService.useSkill(userId, SkillService.SKILL_SPECIAL_ROUND);
            
            // Kích hoạt trạng thái vòng đặc biệt cho phòng chơi
            gameService.enableSpecialRound(roomId, userId);
            
            return ResponseEntity.ok(Map.of("message", "Đã kích hoạt Vòng Đặc Biệt", "room_id", roomId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Sử dụng kỹ năng "Ẩn Danh Bỏ Phiếu" trong lúc thảo luận
     * Sẽ có hiệu lực ở vòng bỏ phiếu tiếp theo
     */
    @PostMapping("/anonymous-vote")
    public ResponseEntity<?> useAnonymousVote(@RequestParam String matchId, @AuthenticationPrincipal String userId) {
        try {
            // Kiểm tra và tiêu xu của người sử dụng
            skillService.useSkill(userId, SkillService.SKILL_ANONYMOUS_VOTE);
            
            // Kích hoạt ẩn danh cho vòng bỏ phiếu tiếp theo
            gameService.enableAnonymousVoting(matchId, userId);
            
            return ResponseEntity.ok(Map.of("message", "Đã kích hoạt Ẩn Danh Bỏ Phiếu cho vòng tiếp theo", "match_id", matchId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
