package com.keywordspy.game.service;

import com.keywordspy.game.model.Transaction;
import com.keywordspy.game.model.User;
import com.keywordspy.game.repository.TransactionRepository;
import com.keywordspy.game.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

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
    public void dailyCheckin(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (user.getLastCheckinDate() != null && user.getLastCheckinDate().equals(LocalDate.now())) {
            throw new RuntimeException("Bạn đã điểm danh hôm nay rồi!");
        }

        int checkinAmount = 200;
        user.setBalance(user.getBalance() + checkinAmount);
        user.setLastCheckinDate(LocalDate.now());
        userRepository.save(user);

        logTransaction(userId, checkinAmount, Transaction.TransactionType.DAILY_CHECKIN, "Điểm danh hàng ngày +200 xu");
    }

    /**
     * Lấy danh sách bảng xếp hạng
     */
    public List<User> getLeaderboard() {
        return userRepository.findAll().stream()
                .sorted((u1, u2) -> Integer.compare(u2.getRankingPoints(), u1.getRankingPoints()))
                .limit(50)
                .toList();
    }

    private void logTransaction(String userId, int amount, Transaction.TransactionType type, String description) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setDescription(description);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }
}
