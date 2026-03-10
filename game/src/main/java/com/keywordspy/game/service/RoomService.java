package com.keywordspy.game.service;

import com.keywordspy.game.model.Room;
import com.keywordspy.game.model.RoomStatus;
import com.keywordspy.game.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ROOM_CODE_LENGTH = 8;

    // Tạo room code ngẫu nhiên 8 ký tự
    private String generateRoomCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    // Tạo phòng mới
    public Room createRoom(String hostId, boolean isPrivate) {
        String roomCode;
        // Đảm bảo room code không bị trùng
        do {
            roomCode = generateRoomCode();
        } while (roomRepository.findByRoomCode(roomCode).isPresent());

        Room room = new Room();
        room.setRoomCode(roomCode);
        room.setHostId(hostId);
        room.setPrivate(isPrivate);
        room.setCurrentPlayers(1); // host là người đầu tiên
        room.setStatus(RoomStatus.waiting);

        return roomRepository.save(room);
    }

    // Tham gia phòng bằng room code
    public Room joinRoom(String roomCode, String userId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (room.getStatus() != RoomStatus.waiting) {
            throw new RuntimeException("Room is not available");
        }

        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            throw new RuntimeException("Room is full");
        }

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
    }
}