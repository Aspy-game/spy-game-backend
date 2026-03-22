package com.keywordspy.game.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "transactions")
public class Transaction {
    @Id
    private String id;
    private String userId;
    private int amount; // Số xu (+ hoặc -)
    private TransactionType type;
    private String description; // Chi tiết thêm (VD: "Thắng ván ID 123")
    private LocalDateTime createdAt;

    public enum TransactionType {
        INITIAL_GIFT,   // Tặng khi tạo tài khoản
        BET,            // Trừ tiền cược
        WIN_REWARD,     // Thưởng thắng ván
        DAILY_CHECKIN,  // Điểm danh hàng ngày
        GUESS_BONUS,    // Thưởng đoán vai
        SKILL_BONUS,    // Thưởng kỹ năng (lừa AI/người chơi)
        RELIEF,         // Cứu trợ khi hết tiền
        ADMIN_ADD       // Admin tặng xu (Test)
    }
}
