package com.keywordspy.game.repository;

import com.keywordspy.game.model.MatchPlayer;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MatchPlayerRepository extends MongoRepository<MatchPlayer, String> {
    List<MatchPlayer> findByMatchId(String matchId);
    List<MatchPlayer> findTop20ByUserIdOrderByIdDesc(String userId);
    long countByUserId(String userId);
}
