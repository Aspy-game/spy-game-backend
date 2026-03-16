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
    private String infectedUserId;   // userId của người bị Tha hóa (null nếu chưa có)

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

    // Tin nhắn giả mạo AI của Spy: roundNumber → nội dung
    // (chỉ lưu 1 tin/round vì Spy chỉ được dùng 1 lần)
    private Map<Integer, String> fakeDescriptions = new HashMap<>();

    // Vote: roundNumber → (voterId → targetId)
    private Map<Integer, Map<String, String>> votes = new HashMap<>();

    // =========================================================
    // SPY ABILITY - EP-04
    // =========================================================

    // Spy đã đoán đúng role trong round hiện tại chưa
    private boolean roleCheckCorrect = false;

    // Round nào Spy đoán đúng (dùng để kiểm tra "đoán đúng round này" vs round cũ)
    private int roleCheckRound = 0;

    // Ability nào đang được mở khóa cho Spy
    // null = chưa mở / không có ability
    private SpyAbility abilityType = null;

    // Spy đã dùng fake-message chưa (dùng 1 lần duy nhất toàn ván)
    // true → mất quyền Tha hóa (trade-off)
    private boolean fakeMessageUsed = false;

    // Spy đã Tha hóa ai chưa (chỉ được tha hóa 1 lần toàn ván)
    private boolean infectUsed = false;

    // =========================================================
    // ROUND RESULT
    // =========================================================
    private String eliminatedUserId;   // userId bị loại round này (null khi round mới bắt đầu)
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
        WAITING,       // Phòng chờ
        ROLE_ASSIGN,   // Server đang assign vai + keyword
        ROLE_CHECK,    // Spy đoán vai trò (Round 2+)
        DESCRIBING,    // Phase mô tả keyword (60s)
        DISCUSSING,    // Phase thảo luận (90s)
        VOTING,        // Phase bỏ phiếu (30s)
        VOTE_TIE,      // Hòa → Sudden Death
        ROUND_RESULT,  // Hiển thị kết quả round
        INFECTION,     // Spy chọn người Tha hóa (khi AI bị loại)
        GAME_OVER      // Game kết thúc
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    // Lấy player theo userId (trả null nếu không tìm thấy)
    public Player getPlayer(String userId) {
        return players.stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    // Lấy danh sách player còn sống
    public List<Player> getAlivePlayers() {
        return players.stream()
                .filter(Player::isAlive)
                .toList();
    }

    // Lấy descriptions của round hiện tại
    public Map<String, String> getCurrentRoundDescriptions() {
        return descriptions.getOrDefault(currentRound, new HashMap<>());
    }

    // Lấy votes của round hiện tại
    public Map<String, String> getCurrentRoundVotes() {
        return votes.getOrDefault(currentRound, new HashMap<>());
    }
}