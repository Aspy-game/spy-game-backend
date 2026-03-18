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

    // Tạo phòng mới
    public Room createRoom(String hostUserId, boolean isPrivate) {
        User host = userRepository.findById(hostUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = new Room();
        room.setRoomCode(generateRoomCode());
        room.setHostId(hostUserId);
        room.setPrivate(isPrivate);
        room.setCurrentPlayers(1);
        room.setStatus(RoomStatus.waiting);
        Room savedRoom = roomRepository.save(room);

        // Thêm host vào room_players
        addPlayerToRoom(savedRoom.getId(), host);

        return savedRoom;
    }

    // Join phòng bằng roomCode
    public Room joinRoom(String roomCode, String userId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (room.getStatus() != RoomStatus.waiting) {
            throw new RuntimeException("Room is not available");
        }
<<<<<<< HEAD

=======
>>>>>>> feature/game-engine
        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            throw new RuntimeException("Room is full");
        }

<<<<<<< HEAD
        room.setCurrentPlayers(room.getCurrentPlayers() + 1);
        return roomRepository.save(room);
    }

    // Rời phòng
    public Room leaveRoom(String roomCode, String userId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (room.getCurrentPlayers() > 0) {
            room.setCurrentPlayers(room.getCurrentPlayers() - 1);
        }

        // Nếu không còn ai thì xóa phòng
        if (room.getCurrentPlayers() == 0) {
            roomRepository.delete(room);
            return null;
        }

        return roomRepository.save(room);
    }

    // Lấy danh sách phòng đang chờ (public)
    public List<Room> getWaitingRooms(int page, int size) {
        return roomRepository.findAll(PageRequest.of(page, size))
                .stream()
                .filter(r -> r.getStatus() == RoomStatus.waiting && !r.isPrivate())
                .toList();
    }

    // Tìm phòng theo room code
    public Optional<Room> findByRoomCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode);
=======
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

        return savedRoom;
    }

    // Rời phòng
    public void leaveRoom(String roomId, String userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        roomPlayerRepository.deleteByRoomIdAndUserId(roomId, userId);

        room.setCurrentPlayers(Math.max(0, room.getCurrentPlayers() - 1));

        // Nếu host rời → assign host mới hoặc đóng phòng
        if (room.getHostId().equals(userId)) {
            List<RoomPlayer> remaining = roomPlayerRepository.findByRoomId(roomId);
            if (remaining.isEmpty()) {
                room.setStatus(RoomStatus.finished);
            } else {
                room.setHostId(remaining.get(0).getUserId());
            }
        }

        roomRepository.save(room);
        broadcastRoomUpdate(room);
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

    // Helper: thêm player vào collection room_players
    private void addPlayerToRoom(String roomId, User user) {
        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(roomId);
        rp.setUserId(user.getId());
        rp.setUsername(user.getUsername());
        rp.setDisplayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
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
>>>>>>> feature/game-engine
    }
}