package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document(collection = "match_players")
public class MatchPlayer {
    @Id
    private String id;

    private String matchId;

    private String userId;

    private PlayerColor color;

    private PlayerRole role;

    private boolean isInfected = false;

    private Integer eliminatedRound;

    private boolean didWin = false;
}
