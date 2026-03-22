package com.keywordspy.game.controller;

import com.keywordspy.game.dto.GameMessage;
import com.keywordspy.game.model.RoomPlayer;
import com.keywordspy.game.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class GameWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RoomService roomService;

    @MessageMapping("/game.sendMessage/{roomId}")
    public void sendMessage(@DestinationVariable String roomId, @Payload GameMessage gameMessage) {
        // Kiểm tra xem người gửi còn trong phòng không
        List<RoomPlayer> players = roomService.getPlayersInRoom(roomId);
        boolean isMember = players.stream()
                .anyMatch(p -> p.getDisplayName().equals(gameMessage.getSender()) || 
                              p.getUsername().equals(gameMessage.getSender()));
        
        if (isMember) {
            messagingTemplate.convertAndSend("/topic/room/" + roomId, gameMessage);
        }
    }

    @MessageMapping("/game.addUser/{roomId}")
    public void addUser(
            @DestinationVariable String roomId,
            @Payload GameMessage gameMessage,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        // Kiểm tra xem user có thực sự trong DB room_players không (đã join qua REST chưa)
        List<RoomPlayer> players = roomService.getPlayersInRoom(roomId);
        boolean isMember = players.stream()
                .anyMatch(p -> p.getDisplayName().equals(gameMessage.getSender()) || 
                              p.getUsername().equals(gameMessage.getSender()));

        if (isMember) {
            headerAccessor.getSessionAttributes().put("username", gameMessage.getSender());
            headerAccessor.getSessionAttributes().put("roomId", roomId);
            
            gameMessage.setType(GameMessage.MessageType.JOIN);
            gameMessage.setContent(gameMessage.getSender() + " joined!");
            messagingTemplate.convertAndSend("/topic/room/" + roomId, gameMessage);
        }
    }
}
