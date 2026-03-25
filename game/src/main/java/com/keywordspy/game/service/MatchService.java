package com.keywordspy.game.service;

import com.keywordspy.game.model.Match;
import com.keywordspy.game.model.MatchPlayer;
import com.keywordspy.game.repository.MatchPlayerRepository;
import com.keywordspy.game.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;

    /**
     * Lấy lịch sử 20 trận gần nhất của user
     */
    public List<Map<String, Object>> getPlayerHistory(String userId) {
        List<MatchPlayer> matchPlayers = matchPlayerRepository.findTop20ByUserIdOrderByIdDesc(userId);
        List<Map<String, Object>> history = new ArrayList<>();

        for (MatchPlayer mp : matchPlayers) {
            Map<String, Object> item = new HashMap<>();
            item.put("match_id", mp.getMatchId());
            item.put("role", mp.getRole());
            item.put("did_win", mp.isDidWin());
            item.put("is_infected", mp.isInfected());
            item.put("eliminated_round", mp.getEliminatedRound());

            matchRepository.findById(mp.getMatchId()).ifPresent(m -> {
                item.put("started_at", m.getStartedAt());
                item.put("ended_at", m.getEndedAt());
                item.put("winner_role", m.getWinnerRole());
            });

            history.add(item);
        }

        return history;
    }
}
