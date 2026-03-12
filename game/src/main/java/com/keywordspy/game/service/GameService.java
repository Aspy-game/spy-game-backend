package com.keywordspy.game.service;

import com.keywordspy.game.manager.GameStateMachine;
import com.keywordspy.game.manager.VoteManager;
import com.keywordspy.game.model.*;
import com.keywordspy.game.model.GameSession.GameState;
import com.keywordspy.game.repository.MatchRepository;
import com.keywordspy.game.repository.RoomRepository;
import com.keywordspy.game.repository.RoomPlayerRepository; // ← thêm dòng này
import com.keywordspy.game.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    // Lưu game session trong memory (thay cho Redis)
    private final Map<String, GameSession> gameSessions = new ConcurrentHashMap<>();

    @Autowired
    private GameStateMachine stateMachine;

    @Autowired
    private VoteManager voteManager;

    @Autowired
    private TimerService timerService;

    @Autowired
    private KeywordService keywordService;
    @Autowired  // ← thêm cái này
private RoomPlayerRepository roomPlayerRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // T-018: Bắt đầu game
    public GameSession startGame(String roomId, String hostUserId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getHostId().equals(hostUserId)) {
            throw new RuntimeException("Only host can start the game");
        }

        if (room.getCurrentPlayers() < 2) {
            throw new RuntimeException("Need at least 2 players to start");
        }

        // Chọn keyword pair
        KeywordPair keyword = keywordService.getRandomKeyword();

        // Tạo Match trong DB
        Match match = new Match();
        match.setRoomId(roomId);
        match.setCivilianKeyword(keyword.getCivilianKeyword());
        match.setSpyKeyword(keyword.getSpyKeyword());
        match.setStatus(MatchStatus.in_progress);
        Match savedMatch = matchRepository.save(match);

        // Tạo GameSession
        GameSession session = new GameSession();
        session.setMatchId(savedMatch.getId());
        session.setRoomId(roomId);
        session.setCivilianKeyword(keyword.getCivilianKeyword());
        session.setSpyKeyword(keyword.getSpyKeyword());
        session.setKeywordPairId(keyword.getId());
        session.setCurrentRound(1);

        // Assign players từ room (tạm thời dùng host + mock players)
        List<Player> players = createPlayers(room, savedMatch.getId());
        session.setPlayers(players);

        // Assign Spy ngẫu nhiên (không phải AI)
        List<Player> humanPlayers = players.stream()
                .filter(p -> !p.isAi())
                .toList();
        Player spy = humanPlayers.get(new Random().nextInt(humanPlayers.size()));
        spy.setRole(PlayerRole.spy);
        session.setSpyUserId(spy.getUserId());
        savedMatch.setSpyUserId(spy.getUserId());
        matchRepository.save(savedMatch);

        // Lưu session
        gameSessions.put(savedMatch.getId(), session);

        // Chuyển state sang ROLE_ASSIGN
        stateMachine.transition(session, GameState.ROLE_ASSIGN);

        // Broadcast role cho từng player
        broadcastRoles(session);

        // Chuyển sang DESCRIBING và bắt đầu timer
        stateMachine.transition(session, GameState.DESCRIBING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.DESCRIBE_DURATION));
        timerService.startDescribeTimer(savedMatch.getId());

        // Broadcast phase mới
        broadcastPhase(session);

        // Update room status
        room.setStatus(RoomStatus.in_game);
        roomRepository.save(room);

        return session;
    }

    // Tạo danh sách players từ room
    private List<Player> createPlayers(Room room, String matchId) {
        List<Player> players = new ArrayList<>();
        List<PlayerColor> colors = new ArrayList<>(Arrays.asList(PlayerColor.values()));
        Collections.shuffle(colors);

        // Lấy tất cả players đã join phòng từ room_players
        List<RoomPlayer> roomPlayers = roomPlayerRepository.findByRoomId(room.getId());

        for (RoomPlayer rp : roomPlayers) {
            Player player = new Player();
            player.setUserId(rp.getUserId());
            player.setUsername(rp.getUsername());
            player.setDisplayName(rp.getDisplayName());
            player.setColor(colors.isEmpty() ? PlayerColor.red : colors.remove(0));
            player.setRole(PlayerRole.civilian);
            player.setAlive(true);
            player.setAi(false);
            players.add(player);
        }

        // Nếu chưa đủ 6 → thêm AI Civilian
        while (players.size() < 6) {
            Player aiPlayer = new Player();
            aiPlayer.setUserId("ai_" + UUID.randomUUID().toString().substring(0, 8));
            aiPlayer.setUsername("AI Civilian");
            aiPlayer.setDisplayName("AI Civilian");
            aiPlayer.setColor(colors.isEmpty() ? PlayerColor.red : colors.remove(0));
            aiPlayer.setRole(PlayerRole.ai_civilian);
            aiPlayer.setAi(true);
            aiPlayer.setAlive(true);
            players.add(aiPlayer);
        }

        return players;
    }

    // T-022: Gửi mô tả keyword
    public void submitDescription(String matchId, String userId, String content) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.DESCRIBING) {
            throw new RuntimeException("Not in describing phase");
        }

        Player player = session.getPlayer(userId);
        if (player == null || !player.isAlive()) {
            throw new RuntimeException("Player not found or eliminated");
        }

        // Validate nội dung
        String[] words = content.trim().split("\\s+");
        if (words.length < 5 || words.length > 20) {
            throw new RuntimeException("Description must be 5-20 words");
        }

        // Lưu description
        session.getDescriptions()
                .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>())
                .put(userId, content);

        // Broadcast descriptions
        broadcastDescriptions(session);

        // Nếu tất cả đã mô tả → chuyển sang DISCUSSING
        if (allPlayersDescribed(session)) {
            timerService.cancelTimer(matchId);
            moveToDiscussing(session);
        }
    }

    // T-025: Gửi chat thảo luận
    public void submitChat(String matchId, String userId, String content) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.DISCUSSING) {
            throw new RuntimeException("Not in discussing phase");
        }

        Player player = session.getPlayer(userId);
        if (player == null || !player.isAlive()) {
            throw new RuntimeException("Player not found or eliminated");
        }

        // Broadcast chat
        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("user_id", userId);
        chatMsg.put("display_name", player.getDisplayName());
        chatMsg.put("color", player.getColor().toString());
        chatMsg.put("content", content);
        chatMsg.put("sent_at", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/game/" + matchId + "/chat", (Object)chatMsg);
    }

    // T-027: Bỏ phiếu
    public void submitVote(String matchId, String voterId, String targetId) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.VOTING) {
            throw new RuntimeException("Not in voting phase");
        }

        Player voter = session.getPlayer(voterId);
        Player target = session.getPlayer(targetId);

        if (voter == null || !voter.isAlive()) {
            throw new RuntimeException("Voter not found or eliminated");
        }
        if (target == null || !target.isAlive()) {
            throw new RuntimeException("Target not found or eliminated");
        }
        if (voterId.equals(targetId)) {
            throw new RuntimeException("Cannot vote for yourself");
        }

        Map<String, String> currentVotes = session.getVotes()
                .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>());

        if (currentVotes.containsKey(voterId)) {
            throw new RuntimeException("Already voted");
        }

        currentVotes.put(voterId, targetId);

        // Broadcast vote count
        broadcastVoteCounts(session);

        // Nếu tất cả đã vote → xử lý kết quả
        if (voteManager.allVoted(session)) {
            timerService.cancelTimer(matchId);
            processVoteResult(session);
        }
    }

    // Xử lý kết quả vote
    private void processVoteResult(GameSession session) {
        String eliminatedId = voteManager.processVotes(session);

        if (eliminatedId == null) {
            // Hòa → Sudden Death
            stateMachine.transition(session, GameState.VOTE_TIE);
            broadcastPhase(session);
        } else {
            // Loại người bị vote nhiều nhất
            eliminatePlayer(session, eliminatedId);
        }
    }

    // Loại player
    private void eliminatePlayer(GameSession session, String userId) {
        Player player = session.getPlayer(userId);
        if (player != null) {
            player.setAlive(false);
            player.setEliminatedRound(session.getCurrentRound());
            session.setEliminatedUserId(userId);
        }

        stateMachine.transition(session, GameState.ROUND_RESULT);
        broadcastRoundResult(session);

        // Kiểm tra win condition
        checkWinCondition(session);
    }

    // Kiểm tra điều kiện thắng
    private void checkWinCondition(GameSession session) {
        Player spy = session.getPlayer(session.getSpyUserId());
        List<Player> alivePlayers = session.getAlivePlayers();

        // Spy bị loại → Civilians thắng
        if (spy != null && !spy.isAlive()) {
            session.setWinnerRole(WinnerRole.civilians);
            stateMachine.transition(session, GameState.GAME_OVER);
            broadcastGameOver(session);
            return;
        }

        // Chỉ còn 1 civilian và spy → Spy thắng
        long aliveHumans = alivePlayers.stream().filter(p -> !p.isAi()).count();
        if (aliveHumans <= 1) {
            session.setWinnerRole(WinnerRole.spy);
            stateMachine.transition(session, GameState.GAME_OVER);
            broadcastGameOver(session);
            return;
        }

        // Tiếp tục round mới
        startNextRound(session);
    }
    // T-021: Role Check (Spy guess role)
    public boolean checkRoleAndUnlockAbility(String matchId, String userId, String guessedRole) {
        GameSession session = getSession(matchId);

        // Chỉ cho phép ở phase ROLE_CHECK
        if (session.getState() != GameState.ROLE_CHECK) {
            throw new RuntimeException("Not in role check phase");
        }

        // Chỉ Spy mới được check
        if (session.getSpyUserId() == null || !session.getSpyUserId().equals(userId)) {
            throw new RuntimeException("Only Spy can perform role check");
        }

        // Nếu đã check đúng rồi thì không cho check lại
        if (session.isRoleCheckCorrect()) {
            return true;
        }

        // Kiểm tra đáp án
        if ("spy".equalsIgnoreCase(guessedRole)) {
            session.setRoleCheckCorrect(true);
            session.setAbilityType(SpyAbility.fake_message); // Mở khóa fake_message
            return true;
        }

        return false;
    }

    public void onRoleCheckPhaseEnd(String matchId) {
    GameSession session = getSession(matchId);
    if (session.getState() == GameState.ROLE_CHECK) {
        stateMachine.transition(session, GameState.DESCRIBING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.DESCRIBE_DURATION));
        timerService.startDescribeTimer(matchId);
        broadcastPhase(session);
    }
}

    // Bắt đầu round mới
    private void startNextRound(GameSession session) {
    session.setCurrentRound(session.getCurrentRound() + 1);

    if (session.getCurrentRound() >= 2) {
        stateMachine.transition(session, GameState.ROLE_CHECK);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.ROLE_CHECK_DURATION));
        timerService.startRoleCheckTimer(session.getMatchId()); // ← thêm dòng này
    } else {
        stateMachine.transition(session, GameState.DESCRIBING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.DESCRIBE_DURATION));
        timerService.startDescribeTimer(session.getMatchId());
    }

    broadcastPhase(session);
}

    // Callback khi hết giờ mô tả
    public void onDescribePhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.DESCRIBING) {
            moveToDiscussing(session);
        }
    }

    // Callback khi hết giờ thảo luận
    public void onDiscussPhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.DISCUSSING) {
            moveToVoting(session);
        }
    }

    // Callback khi hết giờ vote
    public void onVotePhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.VOTING) {
            processVoteResult(session);
        }
    }

    private void moveToDiscussing(GameSession session) {
        stateMachine.transition(session, GameState.DISCUSSING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.DISCUSS_DURATION));
        timerService.startDiscussTimer(session.getMatchId());
        broadcastPhase(session);
    }

    private void moveToVoting(GameSession session) {
        stateMachine.transition(session, GameState.VOTING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.VOTE_DURATION));
        timerService.startVoteTimer(session.getMatchId());
        broadcastPhase(session);
    }

    private boolean allPlayersDescribed(GameSession session) {
        Map<String, String> descriptions = session.getCurrentRoundDescriptions();
        return session.getAlivePlayers().stream()
                .allMatch(p -> descriptions.containsKey(p.getUserId()));
    }

    // Broadcast role cho từng player (private)
    private void broadcastRoles(GameSession session) {
        for (Player player : session.getPlayers()) {
            Map<String, Object> roleMsg = new HashMap<>();
            roleMsg.put("match_id", session.getMatchId());
            roleMsg.put("round", session.getCurrentRound());
            roleMsg.put("role", player.getRole().toString());
            roleMsg.put("color", player.getColor().toString());

            if (player.getRole() == PlayerRole.spy) {
                roleMsg.put("your_keyword", session.getSpyKeyword());
            } else {
                roleMsg.put("your_keyword", session.getCivilianKeyword());
            }

            messagingTemplate.convertAndSendToUser(
                    player.getUserId(),
                    "/queue/role",
                    roleMsg
            );
        }
    }

    // Broadcast phase hiện tại
    private void broadcastPhase(GameSession session) {
        Map<String, Object> phaseMsg = new HashMap<>();
        phaseMsg.put("match_id", session.getMatchId());
        phaseMsg.put("phase", session.getState().toString());
        phaseMsg.put("round", session.getCurrentRound());
        if (session.getPhaseEndTime() != null) {
            phaseMsg.put("phase_end_at", session.getPhaseEndTime().toString());
        }
        phaseMsg.put("players", session.getAlivePlayers().stream().map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("user_id", p.getUserId());
            pm.put("display_name", p.getDisplayName());
            pm.put("color", p.getColor().toString());
            pm.put("is_alive", p.isAlive());
            return pm;
        }).toList());

        messagingTemplate.convertAndSend("/topic/game/" + session.getMatchId(), (Object)phaseMsg);
    }

    // Broadcast descriptions
    private void broadcastDescriptions(GameSession session) {
        Map<String, String> descriptions = session.getCurrentRoundDescriptions();
        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/descriptions",
                (Object) descriptions
        );
    }

    // Broadcast vote counts
    private void broadcastVoteCounts(GameSession session) {
        Map<String, Integer> voteCounts = voteManager.getVoteCounts(session);
        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/votes",
                (Object) voteCounts
        );
    }

    // Broadcast round result
    private void broadcastRoundResult(GameSession session) {
        Player eliminated = session.getPlayer(session.getEliminatedUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("match_id", session.getMatchId());
        result.put("round", session.getCurrentRound());
        if (eliminated != null) {
            result.put("eliminated_user_id", eliminated.getUserId());
            result.put("eliminated_display_name", eliminated.getDisplayName());
            result.put("eliminated_role", eliminated.getRole().toString());
        }

        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/round-result",
                (Object) result
        );
    }

    // Broadcast game over
    private void broadcastGameOver(GameSession session) {
        Map<String, Object> gameOver = new HashMap<>();
        gameOver.put("match_id", session.getMatchId());
        gameOver.put("winner_role", session.getWinnerRole().toString());
        gameOver.put("spy_user_id", session.getSpyUserId());
        gameOver.put("civilian_keyword", session.getCivilianKeyword());
        gameOver.put("spy_keyword", session.getSpyKeyword());

        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/game-over",
                (Object) gameOver
        );
    }

    // Lấy game session
    public GameSession getSession(String matchId) {
        GameSession session = gameSessions.get(matchId);
        if (session == null) {
            throw new RuntimeException("Game session not found: " + matchId);
        }
        return session;
    }

    // Lấy state hiện tại
    public Map<String, Object> getGameState(String matchId, String userId) {
        GameSession session = getSession(matchId);
        Player player = session.getPlayer(userId);

        Map<String, Object> state = new HashMap<>();
        state.put("match_id", matchId);
        state.put("round", session.getCurrentRound());
        state.put("phase", session.getState().toString());
        if (session.getPhaseEndTime() != null) {
            state.put("phase_end_at", session.getPhaseEndTime().toString());
        }
        state.put("players", session.getAlivePlayers().stream().map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("user_id", p.getUserId());
            pm.put("display_name", p.getDisplayName());
            pm.put("color", p.getColor().toString());
            pm.put("is_alive", p.isAlive());
            return pm;
        }).toList());

        if (player != null) {
            state.put("your_role", player.getRole().toString());
            state.put("your_color", player.getColor().toString());
            if (player.getRole() == PlayerRole.spy) {
                state.put("your_keyword", session.getSpyKeyword());
            } else {
                state.put("your_keyword", session.getCivilianKeyword());
            }
        }

        return state;
    }
}