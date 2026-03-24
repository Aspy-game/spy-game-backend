package com.keywordspy.game.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class GameSession {

    // =========================================================
    // IDs
    // =========================================================
    private String matchId;
    private String roomId;
    private String roomCode;

    // =========================================================
    // GAME STATE
    // =========================================================
    private GameState state = GameState.WAITING;
    private int currentRound = 0;

    // =========================================================
    // KEYWORDS
    // =========================================================
    private String civilianKeyword;
    private String spyKeyword;
    private String keywordPairId;

    // =========================================================
    // PLAYERS
    // =========================================================
    private List<Player> players = new ArrayList<>();
    private String spyUserId;
    private String aiPlayerId;
    private String infectedUserId;

    // =========================================================
    // PHASE TIMING
    // =========================================================
    private LocalDateTime phaseStartTime;
    private LocalDateTime phaseEndTime;

    // =========================================================
    // ROUND DATA
    // =========================================================
    // Mô tả thật: roundNumber → (userId → nội dung)
    private Map<Integer, Map<String, String>> descriptions = new HashMap<>();

    // Tin nhắn giả mạo AI của Spy: roundNumber → nội dung (1 tin/round)
    private Map<Integer, String> fakeDescriptions = new HashMap<>();

    // Vote: roundNumber → (voterId → targetId)
    private Map<Integer, Map<String, String>> votes = new HashMap<>();

    // =========================================================
    // VÒNG ĐOÁN VAI TRÒ
    // Diễn ra 1 lần duy nhất sau Vòng 1
    // =========================================================

    // Đã qua Vòng Đoán Vai chưa — ngăn lặp lại từ vòng 2 trở đi
    private boolean roleCheckDone = false;

    // Kết quả đoán của từng người: userId → true/false (đoán đúng hay sai)
    // Dùng để tính xu thưởng civilian và mở khóa ability cho Spy
    private Map<String, Boolean> roleCheckResults = new HashMap<>();

    // =========================================================
    // SPY ABILITY
    // =========================================================

    // Spy có biết mình là Spy không
    // true  = đoán đúng ở Vòng Đoán Vai (dù dùng hay không dùng khả năng)
    // false = đoán sai → Spy không biết mình là Spy cả ván
    private boolean spyKnowsRole = false;

    // Ability được mở: SpyAbility.fake_message, SpyAbility.infection, hoặc null
    private SpyAbility abilityType = null;

    // Spy chủ động từ chối dùng khả năng → biết mình là Spy nhưng không có khả năng
    private boolean spyAbilityDeclined = false;

    // Fake message đã dùng vòng này chưa — reset về false đầu mỗi vòng mới
    // Spy được dùng mỗi vòng 1 lần, miễn AI còn sống
    private boolean fakeMessageUsedThisRound = false;

    // Spy đã Tha hóa ai chưa — chỉ được 1 lần cả ván
    private boolean infectUsed = false;

    // =========================================================
    // REWARD SETTINGS (Admin can adjust)
    // =========================================================
    private int rewardCivilianGuess = 20;
    private int rewardSpyGuess = 50;
    private int rewardInfectedGuess = 30; // Thưởng cho người bị tha hóa đoán đúng

    // =========================================================
    // ROUND RESULT
    // =========================================================
    private String eliminatedUserId;
    private WinnerRole winnerRole;

    // =========================================================
    // TIMESTAMPS
    // =========================================================
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    // =========================================================
    // GAME STATES
    // =========================================================
    public enum GameState {
        WAITING,            // Phòng chờ
        ROLE_ASSIGN,        // Server assign vai + keyword
        DESCRIBING,         // Phase mô tả keyword (60s)
        DISCUSSING,         // Phase thảo luận (90s)
        VOTING,             // Phase bỏ phiếu (30s)
        VOTE_TIE,           // Hòa → Sudden Death
        ROUND_RESULT,       // Hiển thị kết quả round
        ROLE_CHECK,         // Vòng Đoán Vai — 20s tất cả đoán (sau Vòng 1)
        ROLE_CHECK_RESULT,  // 20s hiện kết quả riêng tư + Spy chọn Tha Hóa nếu có
        GAME_OVER           // Game kết thúc
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    public Player getPlayer(String userId) {
        return players.stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    public List<Player> getAlivePlayers() {
        return players.stream()
                .filter(Player::isAlive)
                .toList();
    }

    public Map<String, String> getCurrentRoundDescriptions() {
        return descriptions.getOrDefault(currentRound, new HashMap<>());
    }

    public Map<String, String> getCurrentRoundVotes() {
        return votes.getOrDefault(currentRound, new HashMap<>());
    }

    // Người chơi đã gửi kết quả đoán vai chưa
    public boolean hasSubmittedRoleGuess(String userId) {
        return roleCheckResults.containsKey(userId);
    }

    // Tất cả người còn sống đã đoán xong chưa
    public boolean allPlayersGuessed() {
        return getAlivePlayers().stream()
                .allMatch(p -> roleCheckResults.containsKey(p.getUserId()));
    }
}