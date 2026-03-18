package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "room_players")
public class RoomPlayer {
    @Id
    private String id;

    @Indexed
    private String roomId;

    @Indexed
    private String userId;

    private String username;
    private String displayName;

    private LocalDateTime joinedAt = LocalDateTime.now();
}