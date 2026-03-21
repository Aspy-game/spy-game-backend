package com.keywordspy.game.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "game_settings")
public class GameSettings {
    @Id
    private String id = "global";

    private Integer maxPlayers;
    private Integer minPlayers;
    private Integer spiesCount;

    private Integer describeDuration;
    private Integer discussDuration;
    private Integer voteDuration;
    private Integer roleCheckDuration;
    private Integer roleCheckResultDuration;
}
