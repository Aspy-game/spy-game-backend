package com.keywordspy.game.repository;

import com.keywordspy.game.model.GameSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GameSettingsRepository extends MongoRepository<GameSettings, String> {
}
