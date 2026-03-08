package com.keywordspy.game.repository;

import com.keywordspy.game.model.UserStats;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface UserStatsRepository extends MongoRepository<UserStats, String> {
    Optional<UserStats> findByUserId(String userId);
}
