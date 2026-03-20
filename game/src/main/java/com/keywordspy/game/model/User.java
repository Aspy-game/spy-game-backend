package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
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

    private String passwordHash;
    
    private String role = "ROLE_USER"; // Default role

    // --- ECONOMY SYSTEM ---
    private int balance = 500; // Mặc định tặng 500 xu cho người chơi mới
    private int rankingPoints = 0; // Điểm xếp hạng trọn đời (chỉ tăng không giảm)
    // ----------------------

    private LocalDateTime createdAt = LocalDateTime.now();
}
