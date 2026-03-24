package com.keywordspy.game.model;

import lombok.Data;

@Data
public class Player {
    private String userId;
    private String username;
    private String displayName;
    private PlayerColor color;
    private PlayerRole role;
    private boolean isInfected = false;
    private boolean isAlive = true;
    private int eliminatedRound = -1;
    private boolean isAi = false;
    private int scoreGained = 0;
}