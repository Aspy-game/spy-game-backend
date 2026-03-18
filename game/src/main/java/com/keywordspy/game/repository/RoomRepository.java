package com.keywordspy.game.repository;

import com.keywordspy.game.model.Room;
import com.keywordspy.game.model.RoomStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends MongoRepository<Room, String> {
    Optional<Room> findByRoomCode(String roomCode);
    // Thêm method này để RoomService.getPublicRooms() hoạt động:
    List<Room> findByStatusAndIsPrivate(RoomStatus status, boolean isPrivate);
}