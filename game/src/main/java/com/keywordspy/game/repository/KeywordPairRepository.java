package com.keywordspy.game.repository;

import com.keywordspy.game.model.KeywordPair;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface KeywordPairRepository extends MongoRepository<KeywordPair, String> {
    List<KeywordPair> findByCategory(String category);
}