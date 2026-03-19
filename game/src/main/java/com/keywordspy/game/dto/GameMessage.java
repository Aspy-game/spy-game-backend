package com.keywordspy.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    private String sender;
    private String content;
    private MessageType type;
    private String roomId;

    public enum MessageType {
        JOIN,
        LEAVE,
        CHAT,
        VOTE,
        START,
        END
    }
}
