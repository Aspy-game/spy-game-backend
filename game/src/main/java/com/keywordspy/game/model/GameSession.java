package com.keywordspy.game.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class GameSession {

    // IDs
    private String matchId;
    private String roomId;

    // State
    private GameState state = GameState.WAITING;
    private int currentRound = 0;

    // Keyword
    private String civilianKeyword;
    private String spyKeyword;
    private String keywordPairId;

    // Players
    private List<Player> players = new ArrayList<>();
    private String spyUserId;
    private String aiPlayerId;
    private String infectedUserId;

    // Phase timing
    private LocalDateTime phaseStartTime;
    private LocalDateTime phaseEndTime;

    // Descriptions per round: roundNumber -> (userId -> description)
    private Map<Integer, Map<String, String>> descriptions = new HashMap<>();

    // Fake descriptions by Spy: roundNumber -> content
    private Map<Integer, String> fakeDescriptions = new HashMap<>();

    // Votes per round: roundNumber -> (voterId -> targetId)
    private Map<Integer, Map<String, String>> votes = new HashMap<>();

    // Spy ability
    private boolean roleCheckCorrect = false;
    private boolean abilityUsed = false;
    private SpyAbility abilityType = SpyAbility.none;

    // Round result
    private String eliminatedUserId;
    private WinnerRole winnerRole;

    // Timestamps
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum GameState {
        WAITING,
        ROLE_ASSIGN,
        ROLE_CHECK,
        DESCRIBING,
        DISCUSSING,
        VOTING,
        VOTE_TIE,
        ROUND_RESULT,
        INFECTION,
        GAME_OVER
    }

    // Helper: lấy player theo userId
    public Player getPlayer(String userId) {
        return players.stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    // Helper: lấy danh sách player còn sống
    public List<Player> getAlivePlayers() {
        return players.stream()
                .filter(Player::isAlive)
                .toList();
    }

    // Helper: lấy descriptions của round hiện tại
    public Map<String, String> getCurrentRoundDescriptions() {
        return descriptions.getOrDefault(currentRound, new HashMap<>());
    }

    // Helper: lấy votes của round hiện tại
    public Map<String, String> getCurrentRoundVotes() {
        return votes.getOrDefault(currentRound, new HashMap<>());
    }
}