package com.keywordspy.game.controller;

import com.keywordspy.game.model.Room;
import com.keywordspy.game.model.User;
import com.keywordspy.game.service.RoomService;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    // Lấy user hiện tại từ token
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // T-009: Tạo phòng mới
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody(required = false) Map<String, Object> request) {
        try {
            User user = getCurrentUser();
            boolean isPrivate = false;
            if (request != null && request.containsKey("isPrivate")) {
                isPrivate = (Boolean) request.get("isPrivate");
            }

            Room room = roomService.createRoom(user.getId(), isPrivate);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", room.getId(),
                    "roomCode", room.getRoomCode(),
                    "hostId", room.getHostId(),
                    "maxPlayers", room.getMaxPlayers(),
                    "currentPlayers", room.getCurrentPlayers(),
                    "status", room.getStatus().toString(),
                    "isPrivate", room.isPrivate(),
                    "createdAt", room.getCreatedAt().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // T-013: Danh sách phòng đang chờ
    @GetMapping
    public ResponseEntity<?> getRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<Room> rooms = roomService.getWaitingRooms(page, size);
            return ResponseEntity.ok(rooms);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // T-012: Tham gia phòng bằng room code
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomCode) {
        try {
            User user = getCurrentUser();
            Room room = roomService.joinRoom(roomCode, user.getId());

            return ResponseEntity.ok(Map.of(
                    "id", room.getId(),
                    "roomCode", room.getRoomCode(),
                    "hostId", room.getHostId(),
                    "currentPlayers", room.getCurrentPlayers(),
                    "maxPlayers", room.getMaxPlayers(),
                    "status", room.getStatus().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Rời phòng
    @PostMapping("/{roomCode}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomCode) {
        try {
            User user = getCurrentUser();
            Room room = roomService.leaveRoom(roomCode, user.getId());

            if (room == null) {
                return ResponseEntity.ok(Map.of("message", "Room deleted (no players left)"));
            }

            return ResponseEntity.ok(Map.of(
                    "roomCode", room.getRoomCode(),
                    "currentPlayers", room.getCurrentPlayers(),
                    "message", "Left room successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Xem chi tiết phòng
    @GetMapping("/{roomCode}")
    public ResponseEntity<?> getRoom(@PathVariable String roomCode) {
        try {
            Room room = roomService.findByRoomCode(roomCode)
                    .orElseThrow(() -> new RuntimeException("Room not found"));

            return ResponseEntity.ok(Map.of(
                    "id", room.getId(),
                    "roomCode", room.getRoomCode(),
                    "hostId", room.getHostId(),
                    "currentPlayers", room.getCurrentPlayers(),
                    "maxPlayers", room.getMaxPlayers(),
                    "status", room.getStatus().toString(),
                    "isPrivate", room.isPrivate()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}