package com.keywordspy.game.repository;

import com.keywordspy.game.model.VoteLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface VoteLogRepository extends MongoRepository<VoteLog, String> {
    List<VoteLog> findByRoundId(String roundId);
}
