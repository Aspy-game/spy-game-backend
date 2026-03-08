package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "vote_logs")
public class VoteLog {
    @Id
    private String id;

    private String roundId;

    private String voterId;

    private String targetId;

    private boolean isTieBreak = false;

    private LocalDateTime votedAt = LocalDateTime.now();
}
