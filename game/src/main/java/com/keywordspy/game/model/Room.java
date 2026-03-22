package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "rooms")
public class Room {
    @Id
    private String id;

    @Indexed(unique = true)
    private String roomCode;

    private String hostId;

    private int maxPlayers = 6;

    private int currentPlayers = 0;

    private RoomStatus status = RoomStatus.waiting;

    private boolean isPrivate = false;

    private String adminSelectedSpyId; // ID người chơi được admin chọn làm gián điệp

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime startedAt;
}
