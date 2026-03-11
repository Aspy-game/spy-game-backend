package com.keywordspy.game.service;

import com.keywordspy.game.model.Match;
import com.keywordspy.game.model.Round;
import com.keywordspy.game.model.SpyAbility;
import com.keywordspy.game.repository.MatchRepository;
import com.keywordspy.game.repository.RoundRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class GameService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private RoundRepository roundRepository;

    public Optional<Match> findMatchById(String matchId) {
        return matchRepository.findById(matchId);
    }

    public int getCurrentRoundNumber(String matchId) {
        List<Round> rounds = roundRepository.findByMatchId(matchId);
        if (rounds.isEmpty()) {
            return 1;
        }
        return rounds.stream()
                .mapToInt(Round::getRoundNumber)
                .max()
                .orElse(1);
    }

    public boolean checkRoleAndUnlockAbility(Match match, String userId, String guessedRole) {
        // Chỉ Spy mới được đoán
        if (match.getSpyUserId() == null || !match.getSpyUserId().equals(userId)) {
            return false;
        }

        // Kiểm tra vòng chơi >= 2
        int currentRound = getCurrentRoundNumber(match.getId());
        if (currentRound < 2) {
            return false;
        }

        // Kiểm tra đoán đúng "spy"
        if ("spy".equalsIgnoreCase(guessedRole)) {
            match.setSpyAbilityUnlocked(true);
            match.setUnlockedAbility(SpyAbility.fake_message);
            matchRepository.save(match);
            return true;
        }

        return false;
    }
}
