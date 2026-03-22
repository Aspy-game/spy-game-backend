package com.keywordspy.game.controller;

import com.keywordspy.game.model.*;
import com.keywordspy.game.service.RoomService;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<?> createRoom(@RequestBody(required = false) Map<String, Object> body) {
        try {
            User user = getCurrentUser();
            boolean isPrivate = body != null && Boolean.TRUE.equals(body.get("is_private"));
            String customRoomCode = body != null ? (String) body.get("room_code") : null;
            Room room = roomService.createRoom(user.getId(), isPrivate, customRoomCode);

            return ResponseEntity.status(201).body(Map.of(
                    "room_id", room.getId(),
                    "room_code", room.getRoomCode(),
                    "host", Map.of(
                            "user_id", user.getId(),
                            "display_name", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
                    ),
                    "status", room.getStatus().toString(),
                    "current_players", room.getCurrentPlayers()
            ));


    // POST /api/rooms — Tạo phòng mới
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody(required = false) Map<String, Object> body) {
        try {
            User user = getCurrentUser();
            boolean isPrivate = body != null && Boolean.TRUE.equals(body.get("is_private"));
            Room room = roomService.createRoom(user.getId(), isPrivate);

            return ResponseEntity.status(201).body(Map.of(
                    "room_id", room.getId(),
                    "room_code", room.getRoomCode(),
                    "host", Map.of(
                            "user_id", user.getId(),
                            "display_name", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
                    ),
                    "status", room.getStatus().toString(),
                    "current_players", room.getCurrentPlayers()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    // T-013: Danh sách phòng đang chờ

    @GetMapping
    public ResponseEntity<?> getRooms() {
        try {
            List<Room> rooms = roomService.getWaitingRooms(page, size);
            List<Map<String, Object>> roomList = rooms.stream()
                    .map(this::toRoomResponse)
                    .collect(Collectors.toList());

    // GET /api/rooms — Danh sách phòng công khai
    @GetMapping
    public ResponseEntity<?> getRooms() {
        try {

            List<Room> rooms = roomService.getPublicRooms();
            List<Map<String, Object>> roomList = rooms.stream().map(r -> Map.<String, Object>of(
                    "room_id", r.getId(),
                    "room_code", r.getRoomCode(),
                    "current_players", r.getCurrentPlayers(),
                    "max_players", r.getMaxPlayers(),
                    "status", r.getStatus().toString()
            )).toList();



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
            List<RoomPlayer> players = roomService.getPlayersInRoom(room.getId());

            return ResponseEntity.ok(Map.of(
                    "room_id", room.getId(),
                    "room_code", room.getRoomCode(),
                    "current_players", room.getCurrentPlayers(),
                    "players", players.stream().map(p -> Map.<String, Object>of(
                            "user_id", p.getUserId(),
                            "display_name", p.getDisplayName()
                    )).toList()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    // POST /api/rooms/:roomId/leave — Rời phòng
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomId) {
        try {
            User user = getCurrentUser();
            roomService.leaveRoom(roomId, user.getId());

            return ResponseEntity.ok(Map.of("message", "Left room"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/rooms/:roomId/kick — Kick người chơi
    @PostMapping("/{roomId}/kick")
    public ResponseEntity<?> kickPlayer(@PathVariable String roomId, @RequestBody Map<String, String> body) {
        try {
            User host = getCurrentUser();
            String targetUserId = body.get("user_id");
            roomService.kickPlayer(roomId, host.getId(), targetUserId);
            return ResponseEntity.ok(Map.of("message", "Player kicked"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/rooms/:roomId/transfer-host — Nhường quyền Host
    @PostMapping("/{roomId}/transfer-host")
    public ResponseEntity<?> transferHost(@PathVariable String roomId, @RequestBody Map<String, String> body) {
        try {
            User host = getCurrentUser();
            String newHostId = body.get("user_id");
            roomService.transferHost(roomId, host.getId(), newHostId);
            return ResponseEntity.ok(Map.of("message", "Host rights transferred"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    // GET /api/rooms/:roomId/players — Danh sách players trong phòng
    @GetMapping("/{roomId}/players")
    public ResponseEntity<?> getPlayers(@PathVariable String roomId) {
        try {
            List<RoomPlayer> players = roomService.getPlayersInRoom(roomId);
            return ResponseEntity.ok(Map.of(
                    "room_id", roomId,
                    "players", players.stream().map(p -> Map.<String, Object>of(
                            "user_id", p.getUserId(),
                            "display_name", p.getDisplayName()
                    )).toList()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/rooms/:roomId — Lấy thông tin chi tiết phòng
    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoomDetail(@PathVariable String roomId) {
        try {
            Room room = roomService.getRoomById(roomId);
            List<RoomPlayer> players = roomService.getPlayersInRoom(roomId);
            return ResponseEntity.ok(Map.of(
                    "room_id", room.getId(),
                    "room_code", room.getRoomCode(),
                    "host_id", room.getHostId(),
                    "current_players", room.getCurrentPlayers(),
                    "max_players", room.getMaxPlayers(),
                    "status", room.getStatus().toString(),
                    "is_private", room.isPrivate(),
                    "players", players.stream().map(p -> Map.<String, Object>of(
                            "user_id", p.getUserId(),
                            "display_name", p.getDisplayName()
                    )).toList()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/rooms/code/:roomCode — Tìm phòng bằng mã (dành cho tham gia bằng mã)
    @GetMapping("/code/{roomCode}")
    public ResponseEntity<?> getRoomByCode(@PathVariable String roomCode) {
        try {
            Room room = roomService.getRoomByCode(roomCode);
            return ResponseEntity.ok(Map.of(
                    "room_id", room.getId(),
                    "room_code", room.getRoomCode(),
                    "current_players", room.getCurrentPlayers(),
                    "max_players", room.getMaxPlayers(),
                    "status", room.getStatus().toString(),
                    "is_private", room.isPrivate()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}