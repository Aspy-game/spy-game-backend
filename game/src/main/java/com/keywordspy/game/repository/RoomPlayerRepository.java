package com.keywordspy.game.repository;

import com.keywordspy.game.model.RoomPlayer;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface RoomPlayerRepository extends MongoRepository<RoomPlayer, String> {
    List<RoomPlayer> findByRoomId(String roomId);
    Optional<RoomPlayer> findByRoomIdAndUserId(String roomId, String userId);
    void deleteByRoomIdAndUserId(String roomId, String userId);
    int countByRoomId(String roomId);
}