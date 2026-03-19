package com.keywordspy.game.controller;

import com.keywordspy.game.dto.GameMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class GameWebSocketController {

    @MessageMapping("/game.sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public GameMessage sendMessage(@DestinationVariable String roomId, @Payload GameMessage gameMessage) {
        return gameMessage;
    }

    @MessageMapping("/game.addUser/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public GameMessage addUser(
            @DestinationVariable String roomId,
            @Payload GameMessage gameMessage,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        // Add username in web socket session
        headerAccessor.getSessionAttributes().put("username", gameMessage.getSender());
        headerAccessor.getSessionAttributes().put("roomId", roomId);
        
        gameMessage.setType(GameMessage.MessageType.JOIN);
        gameMessage.setContent(gameMessage.getSender() + " joined!");
        return gameMessage;
    }
}
