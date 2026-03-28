package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "matches")
public class Match {
    @Id
    private String id;

    private String roomId;

    private String civilianKeyword;

    private String spyKeyword;

    private String spyUserId;

    private String aiPlayerId;

    private String infectedUserId;

    private WinnerRole winnerRole;

    private int totalRounds;

    private Integer aiEliminatedRound;
    
    // SKILL SYSTEM
    private boolean isSpecialRound = false;
    private boolean isAnonymousVoting = false;

    private MatchStatus status = MatchStatus.in_progress;

    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime endedAt;
}
