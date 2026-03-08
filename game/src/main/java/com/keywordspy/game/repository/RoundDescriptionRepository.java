package com.keywordspy.game.repository;

import com.keywordspy.game.model.RoundDescription;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface RoundDescriptionRepository extends MongoRepository<RoundDescription, String> {
    List<RoundDescription> findByRoundId(String roundId);
}
