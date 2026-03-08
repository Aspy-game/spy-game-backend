package com.keywordspy.game.repository;

import com.keywordspy.game.model.Match;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MatchRepository extends MongoRepository<Match, String> {
    List<Match> findByRoomId(String roomId);
}
