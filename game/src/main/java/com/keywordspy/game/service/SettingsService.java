package com.keywordspy.game.service;

import com.keywordspy.game.model.GameSettings;
import com.keywordspy.game.repository.GameSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SettingsService {
    @Autowired
    private GameSettingsRepository repo;

    public GameSettings getOrDefault() {
        return repo.findById("global").orElseGet(() -> {
            GameSettings s = new GameSettings();
            s.setId("global");
            return s;
        });
    }

    public GameSettings save(GameSettings incoming) {
        incoming.setId("global");
        return repo.save(incoming);
    }

    public Optional<GameSettings> find() {
        return repo.findById("global");
    }
}
