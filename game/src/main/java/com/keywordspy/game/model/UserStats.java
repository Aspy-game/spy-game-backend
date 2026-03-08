package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "user_stats")
public class UserStats {
    @Id
    private String id;

    private String userId;

    private int totalGames = 0;

    private int winsCivilian = 0;

    private int winsSpy = 0;

    private int winsInfected = 0;

    private int timesAsSpy = 0;

    private int timesInfected = 0;

    private int correctVotes = 0;

    private LocalDateTime updatedAt = LocalDateTime.now();
}
