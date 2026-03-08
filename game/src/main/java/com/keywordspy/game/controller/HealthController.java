package com.keywordspy.game.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping("/api/health")
    public Map<String, Object> healthCheck() {
        try {
            mongoTemplate.getDb().listCollectionNames();
            return Map.of("status", "UP", "database", "MongoDB Connected");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }
}
