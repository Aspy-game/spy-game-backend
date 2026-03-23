package com.keywordspy.game.controller;

import com.keywordspy.game.model.Transaction;
import com.keywordspy.game.model.User;
import com.keywordspy.game.repository.TransactionRepository;
import com.keywordspy.game.service.EconomyService;
import com.keywordspy.game.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/economy")
@RequiredArgsConstructor
public class EconomyController {

    private final EconomyService economyService;
    private final UserService userService;
    private final TransactionRepository transactionRepository;

    private User getCurrentUser(Authentication auth) {
        return userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // GET /api/economy/balance - Lấy số dư và điểm rank
    @GetMapping("/balance")
    public ResponseEntity<?> getBalance(Authentication auth) {
        User user = getCurrentUser(auth);
        Map<String, Object> response = new HashMap<>();
        response.put("balance", user.getBalance());
        response.put("ranking_points", user.getRankingPoints());
        response.put("rank_tier", calculateRankTier(user.getRankingPoints()));
        return ResponseEntity.ok(response);
    }

    // GET /api/economy/daily-checkin/status - Kiểm tra trạng thái điểm danh hôm nay
    @GetMapping("/daily-checkin/status")
    public ResponseEntity<?> getCheckinStatus(Authentication auth) {
        User user = getCurrentUser(auth);
        boolean alreadyCheckedIn = economyService.hasCheckedInToday(user.getId());
        Map<String, Object> response = new HashMap<>();
        response.put("canCheckin", !alreadyCheckedIn);
        response.put("todayReward", 200);
        return ResponseEntity.ok(response);
    }

    // POST /api/economy/daily-checkin - Điểm danh nhận 200 xu
    @PostMapping("/daily-checkin")
    public ResponseEntity<?> dailyCheckin(Authentication auth) {
        try {
            User user = getCurrentUser(auth);
            economyService.dailyCheckin(user.getId());
            return ResponseEntity.ok(Map.of("message", "Điểm danh thành công! +200 xu"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/economy/relief - Nhận cứu trợ 50 xu (khi balance < 10)
    @PostMapping("/relief")
    public ResponseEntity<?> getRelief(Authentication auth) {
        try {
            User user = getCurrentUser(auth);
            economyService.applyRelief(user.getId());
            return ResponseEntity.ok(Map.of("message", "Nhận cứu trợ thành công! +50 xu"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/economy/leaderboard - Bảng xếp hạng
    @GetMapping("/leaderboard")
    public ResponseEntity<?> getLeaderboard() {
        List<User> topUsers = economyService.getLeaderboard();
        List<Map<String, Object>> response = topUsers.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("username", u.getUsername());
            m.put("display_name", u.getDisplayName());
            m.put("ranking_points", u.getRankingPoints());
            m.put("rank_tier", calculateRankTier(u.getRankingPoints()));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // GET /api/economy/transactions - Lịch sử giao dịch
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(Authentication auth) {
        User user = getCurrentUser(auth);
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return ResponseEntity.ok(transactions);
    }

    private String calculateRankTier(int points) {
        if (points <= 1000) return "Bronze";
        if (points <= 3000) return "Silver";
        if (points <= 7000) return "Gold";
        if (points <= 15000) return "Platinum";
        return "Diamond";
    }
}
