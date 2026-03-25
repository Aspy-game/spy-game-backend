package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Document(collection = "users")
public class User {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String displayName;

    private String avatarUrl;

    private String passwordHash;
    
    private Role role = Role.ROLE_USER; // Default role

    private boolean active = true;

    // --- ECONOMY SYSTEM ---
    private int balance = 500; // Mặc định tặng 500 xu cho người chơi mới
    private int rankingPoints = 0; // Điểm xếp hạng trọn đời (chỉ tăng không giảm)
    private LocalDate lastCheckinDate; // Ngày điểm danh gần nhất
    private int checkinStreak = 0; // Số ngày điểm danh liên tiếp (1-7)
    // ----------------------

    // --- FORGOT PASSWORD ---
    private String resetToken;
    private LocalDateTime resetTokenExpiry;
    // ----------------------

    private LocalDateTime createdAt = LocalDateTime.now();
}
