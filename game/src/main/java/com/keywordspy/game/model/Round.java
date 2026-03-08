package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "rounds")
public class Round {
    @Id
    private String id;

    private String matchId;

    private int roundNumber;

    private String eliminatedUserId;

    private String eliminatedRole;

    private int tieCount = 0;

    private SpyAbility spyUsedAbility = SpyAbility.none;

    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime endedAt;
}
