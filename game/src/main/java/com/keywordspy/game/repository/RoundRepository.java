package com.keywordspy.game.repository;

import com.keywordspy.game.model.Round;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface RoundRepository extends MongoRepository<Round, String> {
    List<Round> findByMatchId(String matchId);
}
