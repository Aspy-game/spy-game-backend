package com.keywordspy.game.service;

import com.keywordspy.game.model.*;
import com.keywordspy.game.repository.RoomPlayerRepository;
import com.keywordspy.game.repository.RoomRepository;
import com.keywordspy.game.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomPlayerRepository roomPlayerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private GameService gameService;

    // Tạo phòng mới
    public Room createRoom(String hostUserId, boolean isPrivate, String customRoomCode) {
        User host = userRepository.findById(hostUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = new Room();
        if (customRoomCode != null && !customRoomCode.trim().isEmpty()) {
            // Check if exists
            if (roomRepository.findByRoomCode(customRoomCode).isPresent()) {
                throw new RuntimeException("Room code already exists");
            }
            room.setRoomCode(customRoomCode);
        } else {
            room.setRoomCode(generateRoomCode());
        }
        room.setHostId(hostUserId);
        room.setPrivate(isPrivate);
        room.setCurrentPlayers(0);
        room.setMaxPlayers(6); // Mặc định 6 người chơi
        room.setStatus(RoomStatus.waiting);
        Room savedRoom = roomRepository.save(room);

        // Thêm host vào room_players và cập nhật currentPlayers
        return joinRoom(savedRoom.getRoomCode(), hostUserId);
    }

    // Join phòng bằng roomCode

    public Room joinRoom(String roomCode, String userId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (room.getStatus() != RoomStatus.waiting) {
            throw new RuntimeException("Room is not available");
        }

        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            throw new RuntimeException("Room is full");
        }

        // Kiểm tra đã trong phòng chưa
        if (roomPlayerRepository.findByRoomIdAndUserId(room.getId(), userId).isPresent()) {
            return room; // Đã trong phòng rồi, trả về luôn
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        addPlayerToRoom(room.getId(), user);

        // Cập nhật số người
        room.setCurrentPlayers(room.getCurrentPlayers() + 1);
        Room savedRoom = roomRepository.save(room);

        // Broadcast cập nhật lobby
        broadcastRoomUpdate(savedRoom);
        broadcastLobbyRoomEvent(savedRoom, "UPDATED");

        return savedRoom;
    }

    // Rời phòng
    public void leaveRoom(String roomId, String userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        System.out.println("[ROOM-DEBUG] User " + userId + " leaving room " + roomId + ". Room status: " + room.getStatus());
        // Nếu đang trong trận -> Xử lý AFK
        if (room.getStatus() == RoomStatus.in_game) {
            System.out.println("[ROOM-DEBUG] Room is in_game, triggering handlePlayerQuit");
            gameService.handlePlayerQuit(roomId, userId);
        }

        roomPlayerRepository.deleteByRoomIdAndUserId(roomId, userId);

        room.setCurrentPlayers(Math.max(0, room.getCurrentPlayers() - 1));

        // Nếu không còn ai -> xóa phòng luôn
        if (room.getCurrentPlayers() <= 0) {
            roomRepository.deleteById(roomId);
            broadcastLobbyRoomEvent(room, "DELETED");
            return;
        }

        // Nếu host rời → assign host mới
        if (room.getHostId().equals(userId)) {
            List<RoomPlayer> remaining = roomPlayerRepository.findByRoomId(roomId);
            if (!remaining.isEmpty()) {
                room.setHostId(remaining.get(0).getUserId());
            }
        }

        roomRepository.save(room);
        broadcastRoomUpdate(room);
        broadcastLobbyRoomEvent(room, "UPDATED");
    }

    // Nhường quyền Host
    public void transferHost(String roomId, String currentHostId, String newHostId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getHostId().equals(currentHostId)) {
            throw new RuntimeException("Only host can transfer rights");
        }

        // Kiểm tra newHost có trong phòng không
        boolean isInRoom = roomPlayerRepository.findByRoomId(roomId).stream()
                .anyMatch(p -> p.getUserId().equals(newHostId));
        if (!isInRoom) {
            throw new RuntimeException("New host must be in the room");
        }

        room.setHostId(newHostId);
        roomRepository.save(room);
        broadcastRoomUpdate(room);
    }

    // Kick người chơi
    public void kickPlayer(String roomId, String hostId, String targetUserId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getHostId().equals(hostId)) {
            throw new RuntimeException("Only host can kick players");
        }

        if (hostId.equals(targetUserId)) {
            throw new RuntimeException("Host cannot kick themselves");
        }

        roomPlayerRepository.deleteByRoomIdAndUserId(roomId, targetUserId);
        room.setCurrentPlayers(Math.max(0, room.getCurrentPlayers() - 1));
        
        roomRepository.save(room);
        
        // Phát thông báo KICKED tới toàn bộ phòng để FE tự động xử lý (người bị kick sẽ so sánh ID và out)
        Map<String, Object> kickMsg = new HashMap<>();
        kickMsg.put("type", "PLAYER_KICKED");
        kickMsg.put("room_id", roomId);
        kickMsg.put("target_user_id", targetUserId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, (Object) kickMsg);

        broadcastRoomUpdate(room);
        broadcastLobbyRoomEvent(room, "UPDATED");
    }

    // Admin add player (for test)
    public Room addPlayerAdmin(String roomId, String identifier) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new RuntimeException("User not found with username/email: " + identifier));

        String targetUserId = user.getId();

        // Kiểm tra xem đã có trong phòng chưa
        boolean alreadyIn = roomPlayerRepository.findByRoomId(roomId).stream()
                .anyMatch(p -> p.getUserId().equals(targetUserId));
        
        if (!alreadyIn) {
            addPlayerToRoom(roomId, user);
            room.setCurrentPlayers(room.getCurrentPlayers() + 1);
            roomRepository.save(room);
        }

        broadcastRoomUpdate(room);
        broadcastLobbyRoomEvent(room, "UPDATED");
        return room;
    }

    // Danh sách phòng công khai đang chờ
    public List<Room> getPublicRooms() {
        return roomRepository.findByStatusAndIsPrivate(RoomStatus.waiting, false);
    }

    // Danh sách players trong phòng
    public List<RoomPlayer> getPlayersInRoom(String roomId) {
        return roomPlayerRepository.findByRoomId(roomId);
    }

    // Lấy room theo ID
    public Room getRoomById(String roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
    }

    public Room getRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found with code: " + roomCode));
    }

    // Helper: thêm player vào collection room_players
    private void addPlayerToRoom(String roomId, User user) {
        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(roomId);
        rp.setUserId(user.getId());
        // Username dùng cho WebSocket private phải khớp với Principal name trong Spring Security
        String wsUsername = user.getUsername() != null ? user.getUsername() : user.getEmail();
        rp.setUsername(wsUsername);
        rp.setDisplayName(user.getDisplayName() != null ? user.getDisplayName() : wsUsername);
        roomPlayerRepository.save(rp);
    }

    // Helper: generate room code 8 ký tự
    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        // Đảm bảo unique
        if (roomRepository.findByRoomCode(code.toString()).isPresent()) {
            return generateRoomCode();
        }
        return code.toString();
    }

    // Broadcast cập nhật phòng qua WebSocket
    private void broadcastRoomUpdate(Room room) {
        List<RoomPlayer> players = roomPlayerRepository.findByRoomId(room.getId());
        Map<String, Object> update = new HashMap<>();
        update.put("room_id", room.getId());
        update.put("room_code", room.getRoomCode());
        update.put("host_id", room.getHostId());
        update.put("current_players", room.getCurrentPlayers());
        update.put("status", room.getStatus().toString());
        update.put("players", players.stream().map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("user_id", p.getUserId());
            pm.put("display_name", p.getDisplayName());
            return pm;
        }).toList());

        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), (Object) update);

    }

    public List<Room> findAllRooms() {
        return roomRepository.findAll();
    }

    public void deleteRoom(String roomId) {
        Optional<Room> before = roomRepository.findById(roomId);
        roomRepository.deleteById(roomId);
        before.ifPresent(r -> broadcastLobbyRoomEvent(r, "DELETED"));
    }

    public long countActiveRooms() {
        return roomRepository.count();
    }

    private void broadcastLobbyRoomEvent(Room room, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ROOM_" + type);
        payload.put("room_id", room.getId());
        payload.put("room_code", room.getRoomCode());
        payload.put("current_players", room.getCurrentPlayers());
        payload.put("max_players", room.getMaxPlayers());
        payload.put("status", room.getStatus().toString());
        payload.put("is_private", room.isPrivate());
        messagingTemplate.convertAndSend("/topic/rooms/lobby", (Object) payload);
    }
}
