package com.keywordspy.game.service;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import com.keywordspy.game.model.Transaction;
import com.keywordspy.game.model.User;
import com.keywordspy.game.repository.TransactionRepository;
import com.keywordspy.game.repository.UserRepository;
import com.keywordspy.game.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class EconomyService {

    public static final int SPECIAL_ROOM_COST = 500;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final UserStatsRepository userStatsRepository;

    // --- ECONOMY SYSTEM LOGIC ---

    /**
     * Khấu trừ tiền cược khi bắt đầu ván đấu
     */
    @Transactional
    public void deductEntryFee(String userId, int fee) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.getBalance() < fee) {
            throw new RuntimeException("Insufficient balance for user: " + userId);
        }

        user.setBalance(user.getBalance() - fee);
        userRepository.save(user);

        logTransaction(userId, -fee, Transaction.TransactionType.BET, "Phí vào cửa ván đấu");
    }

    /**
     * Cộng thưởng xu và điểm xếp hạng
     */
    @Transactional
    public void addReward(String userId, int amount, Transaction.TransactionType type, String description, boolean addToRanking) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.setBalance(user.getBalance() + amount);
        
        if (addToRanking) {
            user.setRankingPoints(user.getRankingPoints() + amount);
        }

        userRepository.save(user);
        logTransaction(userId, amount, type, description);
    }

    /**
     * Quà cứu trợ khi hết tiền (Balance < 10)
     */
    @Transactional
    public void applyRelief(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.getBalance() >= 10) {
            throw new RuntimeException("User still has enough balance for relief");
        }

        int reliefAmount = 50;
        user.setBalance(user.getBalance() + reliefAmount);
        userRepository.save(user);

        logTransaction(userId, reliefAmount, Transaction.TransactionType.RELIEF, "Quà cứu trợ Bankruptcy Relief");
    }

    /**
     * Kiểm tra xem user đã điểm danh hôm nay chưa
     */
    public boolean hasCheckedInToday(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return user.getLastCheckinDate() != null && user.getLastCheckinDate().equals(LocalDate.now());
    }

    /**
     * Điểm danh hàng ngày - kiểm tra trùng ngày
     */
    @Transactional
    public Map<String, Object> dailyCheckin(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        LocalDate today = LocalDate.now();
        if (user.getLastCheckinDate() != null && user.getLastCheckinDate().equals(today)) {
            throw new RuntimeException("Bạn đã điểm danh hôm nay rồi!");
        }

        // Tính toán streak
        int newStreak = 1;
        if (user.getLastCheckinDate() != null) {
            if (user.getLastCheckinDate().plusDays(1).equals(today)) {
                // Điểm danh liên tiếp
                newStreak = (user.getCheckinStreak() % 7) + 1;
            } else {
                // Bị đứt chuỗi
                newStreak = 1;
            }
        }

        // Phần thưởng theo ngày: [10, 10, 10, 10, 20, 20, 30]
        int[] rewards = {10, 10, 10, 10, 20, 20, 30};
        int checkinAmount = rewards[newStreak - 1];

        user.setBalance(user.getBalance() + checkinAmount);
        user.setLastCheckinDate(today);
        user.setCheckinStreak(newStreak);
        userRepository.save(user);

        logTransaction(userId, checkinAmount, Transaction.TransactionType.DAILY_CHECKIN, 
            "Điểm danh hàng ngày (Ngày " + newStreak + ") +" + checkinAmount + " xu");

        Map<String, Object> result = new HashMap<>();
        result.put("amount", checkinAmount);
        result.put("streak", newStreak);
        return result;
    }

    /**
     * Lấy danh sách bảng xếp hạng theo tiêu chí
     */
    public List<Map<String, Object>> getLeaderboard(String type) {
        if ("spy".equalsIgnoreCase(type) || "civilian".equalsIgnoreCase(type)) {
            return userStatsRepository.findAll().stream()
                .sorted((s1, s2) -> "spy".equalsIgnoreCase(type) 
                    ? Integer.compare(s2.getWinsSpy(), s1.getWinsSpy())
                    : Integer.compare(s2.getWinsCivilian(), s1.getWinsCivilian()))
                .limit(50)
                .map(stats -> {
                    Map<String, Object> m = new HashMap<>();
                    userRepository.findById(stats.getUserId()).ifPresent(u -> {
                        m.put("username", u.getUsername());
                        m.put("display_name", u.getDisplayName());
                        m.put("avatar_url", u.getAvatarUrl());
                    });
                    m.put("score", "spy".equalsIgnoreCase(type) ? stats.getWinsSpy() : stats.getWinsCivilian());
                    return m;
                })
                .filter(m -> m.containsKey("username"))
                .toList();
        }

        // Mặc định xếp theo Xu (balance)
        return userRepository.findAll().stream()
                .sorted((u1, u2) -> Integer.compare(u2.getBalance(), u1.getBalance()))
                .limit(50)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("username", u.getUsername());
                    m.put("display_name", u.getDisplayName());
                    m.put("avatar_url", u.getAvatarUrl());
                    m.put("score", u.getBalance());
                    return m;
                })
                .toList();
    }

    @Transactional
    public void deductBalance(String userId, int amount, Transaction.TransactionType type, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.getBalance() < amount) {
            throw new RuntimeException("Số dư không đủ");
        }

        user.setBalance(user.getBalance() - amount);
        userRepository.save(user);

        logTransaction(userId, -amount, type, description);
    }

    private void logTransaction(String userId, int amount, Transaction.TransactionType type, String description) {
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(amount)
                .type(type)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);
    }
}
