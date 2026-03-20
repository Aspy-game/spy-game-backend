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

    private String avatarUrl;

    private String passwordHash;
    
    private String role = "ROLE_USER"; // Default role

    private LocalDateTime createdAt = LocalDateTime.now();
}
