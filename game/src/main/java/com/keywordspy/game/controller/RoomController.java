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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Map<String, Object> toRoomResponse(Room room) {
        Map<String, Object> map = new HashMap<>();
        map.put("room_id", room.getId());
        map.put("room_code", room.getRoomCode());
        map.put("host_id", room.getHostId());
        map.put("max_players", room.getMaxPlayers());
        map.put("current_players", room.getCurrentPlayers());
        map.put("status", room.getStatus().toString());
        map.put("is_private", room.isPrivate());
        map.put("created_at", room.getCreatedAt().toString());
        return map;
    }

    // T-009: Tạo phòng mới
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody(required = false) Map<String, Object> request) {
        try {
            User user = getCurrentUser();
            boolean isPrivate = false;
            if (request != null && request.containsKey("is_private")) {
                isPrivate = (Boolean) request.get("is_private");
            }

            Room room = roomService.createRoom(user.getId(), isPrivate);

            Map<String, Object> response = toRoomResponse(room);
            response.put("host", Map.of(
                    "user_id", user.getId(),
                    "display_name", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
            ));

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // T-013: Danh sách phòng đang chờ
    @GetMapping
    public ResponseEntity<?> getRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<Room> rooms = roomService.getWaitingRooms(page, size);
            List<Map<String, Object>> roomList = rooms.stream()
                    .map(this::toRoomResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "rooms", roomList,
                    "total", roomList.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // T-012: Tham gia phòng bằng room code
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomCode) {
        try {
            User user = getCurrentUser();
            Room room = roomService.joinRoom(roomCode, user.getId());
            return ResponseEntity.ok(toRoomResponse(room));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            return ResponseEntity.ok(Map.of("message", "Left room"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Xem chi tiết phòng
    @GetMapping("/{roomCode}")
    public ResponseEntity<?> getRoom(@PathVariable String roomCode) {
        try {
            Room room = roomService.findByRoomCode(roomCode)
                    .orElseThrow(() -> new RuntimeException("Room not found"));
            return ResponseEntity.ok(toRoomResponse(room));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}