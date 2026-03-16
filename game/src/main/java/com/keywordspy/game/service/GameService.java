package com.keywordspy.game.service;

import com.keywordspy.game.manager.GameStateMachine;
import com.keywordspy.game.manager.VoteManager;
import com.keywordspy.game.model.*;
import com.keywordspy.game.model.GameSession.GameState;
import com.keywordspy.game.repository.MatchRepository;
import com.keywordspy.game.repository.RoomRepository;
import com.keywordspy.game.repository.RoomPlayerRepository;
import com.keywordspy.game.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    // =========================================================
    // DEPENDENCIES
    // =========================================================

    private final Map<String, GameSession> gameSessions = new ConcurrentHashMap<>();

    @Autowired private GameStateMachine stateMachine;
    @Autowired private VoteManager voteManager;
    @Autowired private TimerService timerService;
    @Autowired private KeywordService keywordService;
    @Autowired private RoomPlayerRepository roomPlayerRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // =========================================================
    // SECTION 1: GAME SETUP
    // =========================================================

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

        KeywordPair keyword = keywordService.getRandomKeyword();

        Match match = new Match();
        match.setRoomId(roomId);
        match.setCivilianKeyword(keyword.getCivilianKeyword());
        match.setSpyKeyword(keyword.getSpyKeyword());
        match.setStatus(MatchStatus.in_progress);
        Match savedMatch = matchRepository.save(match);

        GameSession session = new GameSession();
        session.setMatchId(savedMatch.getId());
        session.setRoomId(roomId);
        session.setCivilianKeyword(keyword.getCivilianKeyword());
        session.setSpyKeyword(keyword.getSpyKeyword());
        session.setKeywordPairId(keyword.getId());
        session.setCurrentRound(1);

        List<Player> players = createPlayers(room, savedMatch.getId());
        session.setPlayers(players);

        // Assign Spy ngẫu nhiên (không phải AI)
        List<Player> humanPlayers = players.stream().filter(p -> !p.isAi()).toList();
        Player spy = humanPlayers.get(new Random().nextInt(humanPlayers.size()));
        spy.setRole(PlayerRole.spy);
        session.setSpyUserId(spy.getUserId());
        savedMatch.setSpyUserId(spy.getUserId());
        matchRepository.save(savedMatch);

        gameSessions.put(savedMatch.getId(), session);

        stateMachine.transition(session, GameState.ROLE_ASSIGN);
        broadcastRoles(session);

        stateMachine.transition(session, GameState.DESCRIBING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.DESCRIBE_DURATION));
        timerService.startDescribeTimer(savedMatch.getId());
        broadcastPhase(session);

        room.setStatus(RoomStatus.in_game);
        roomRepository.save(room);

        return session;
    }

    private List<Player> createPlayers(Room room, String matchId) {
        List<Player> players = new ArrayList<>();
        List<PlayerColor> colors = new ArrayList<>(Arrays.asList(PlayerColor.values()));
        Collections.shuffle(colors);

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

    // =========================================================
    // SECTION 2: PHASE ACTIONS (Describe / Chat / Vote)
    // =========================================================

    // T-022: Gửi mô tả keyword
    public void submitDescription(String matchId, String userId, String content) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.DESCRIBING) {
            throw new RuntimeException("Not in describing phase");
        }

        Player player = getAlivePlayer(session, userId);

        String[] words = content.trim().split("\\s+");
        if (words.length < 5 || words.length > 20) {
            throw new RuntimeException("Description must be 5-20 words");
        }

        session.getDescriptions()
                .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>())
                .put(userId, content);

        broadcastDescriptions(session);

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

        Player player = getAlivePlayer(session, userId);

        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("user_id", userId);
        chatMsg.put("display_name", player.getDisplayName());
        chatMsg.put("color", player.getColor().toString());
        chatMsg.put("content", content);
        chatMsg.put("sent_at", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/game/" + matchId + "/chat", (Object) chatMsg);
    }

    // T-027: Bỏ phiếu
    public void submitVote(String matchId, String voterId, String targetId) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.VOTING) {
            throw new RuntimeException("Not in voting phase");
        }

        Player voter = getAlivePlayer(session, voterId);
        Player target = session.getPlayer(targetId);

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
        broadcastVoteCounts(session);

        if (voteManager.allVoted(session)) {
            timerService.cancelTimer(matchId);
            processVoteResult(session);
        }
    }

    // =========================================================
    // SECTION 3: SPY ABILITIES
    // =========================================================

    // T-021: Role Check - Spy đoán vai trò (Round 2+)
    public Map<String, Object> checkRoleAndUnlockAbility(String matchId, String userId, String guessedRole) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.ROLE_CHECK) {
            throw new RuntimeException("Not in role check phase");
        }
        if (!userId.equals(session.getSpyUserId())) {
            throw new RuntimeException("Only Spy can perform role check");
        }
        if (session.isRoleCheckCorrect()) {
            // Đã check đúng rồi, trả lại kết quả cũ
            return buildRoleCheckResponse(session);
        }

        if (!"spy".equalsIgnoreCase(guessedRole)) {
            return Map.of("correct", false, "message", "Đoán sai rồi!");
        }

        // Đoán đúng - xác định ability nào được mở
        session.setRoleCheckCorrect(true);

        boolean aiAlive = isAiAlive(session);
        if (aiAlive) {
            // AI còn sống → mở fake_message
            // Chú ý: nếu sau này dùng fake_message thì MẤT quyền infect (trade-off)
            session.setAbilityType(SpyAbility.fake_message);
        } else if (!session.isInfectUsed()) {
            // AI đã chết + chưa tha hóa ai → mở infect
            session.setAbilityType(SpyAbility.infection);
        } else {
            // AI đã chết + đã tha hóa rồi → không còn ability
            session.setAbilityType(null);
        }

        // Notify private cho Spy
        notifySpyAbilityUnlocked(session);

        return buildRoleCheckResponse(session);
    }

    // T-023: Tin nhắn giả mạo AI
    public Map<String, Object> useFakeMessageAbility(String matchId, String userId, String content) {
        GameSession session = getSession(matchId);

        // Kiểm tra phase: được dùng trong DESCRIBING hoặc DISCUSSING
        if (session.getState() != GameState.DESCRIBING && session.getState() != GameState.DISCUSSING) {
            throw new RuntimeException("Chỉ dùng được trong phase Mô tả hoặc Thảo luận");
        }
        if (!userId.equals(session.getSpyUserId())) {
            throw new RuntimeException("Chỉ Spy mới có thể dùng năng lực này");
        }
        if (!session.isRoleCheckCorrect() || session.getAbilityType() != SpyAbility.fake_message) {
            throw new RuntimeException("Năng lực chưa được mở khóa hoặc không hợp lệ");
        }
        if (!isAiAlive(session)) {
            throw new RuntimeException("AI đã bị loại, không thể dùng fake message");
        }
        if (session.isFakeMessageUsed()) {
            throw new RuntimeException("Đã dùng fake message rồi");
        }

        String[] words = content.trim().split("\\s+");
        if (words.length < 5 || words.length > 20) {
            throw new RuntimeException("Nội dung phải từ 5-20 từ");
        }

        Player aiPlayer = getAiPlayer(session);

        // Đánh dấu đã dùng → MẤT quyền infect sau này (trade-off)
        session.setFakeMessageUsed(true);
        session.getFakeDescriptions().put(session.getCurrentRound(), content);

        // Broadcast vào /chat (đúng channel), hiển thị như tin của AI
        // FE nhận được sẽ không biết là fake vì user_id và display_name là của AI
        Map<String, Object> fakeMsg = new HashMap<>();
        fakeMsg.put("user_id", aiPlayer.getUserId());
        fakeMsg.put("display_name", aiPlayer.getDisplayName());
        fakeMsg.put("color", aiPlayer.getColor().toString());
        fakeMsg.put("content", content);
        fakeMsg.put("sent_at", LocalDateTime.now().toString());
        // KHÔNG đưa is_fake vào đây - client không được biết

        messagingTemplate.convertAndSend("/topic/game/" + matchId + "/chat", (Object) fakeMsg);

        return Map.of("sent", true, "displayed_as", aiPlayer.getDisplayName());
    }

    // T-024: Tha hóa (Infect)
    public Map<String, Object> infectPlayer(String matchId, String spyUserId, String targetUserId) {
        GameSession session = getSession(matchId);

        if (!spyUserId.equals(session.getSpyUserId())) {
            throw new RuntimeException("Chỉ Spy mới có thể tha hóa");
        }
        if (isAiAlive(session)) {
            throw new RuntimeException("AI vẫn còn sống. Dùng fake message thay vì tha hóa");
        }
        if (session.isFakeMessageUsed()) {
            throw new RuntimeException("Đã dùng fake message → mất quyền tha hóa");
        }
        if (session.isInfectUsed()) {
            throw new RuntimeException("Đã tha hóa rồi");
        }
        if (session.getState() != GameState.INFECTION) {
            throw new RuntimeException("Không phải phase INFECTION");
        }

        Player target = session.getPlayer(targetUserId);
        if (target == null || !target.isAlive()) {
            throw new RuntimeException("Target không tồn tại hoặc đã bị loại");
        }
        if (targetUserId.equals(spyUserId)) {
            throw new RuntimeException("Không thể tha hóa chính mình");
        }

        // Thực hiện tha hóa
        target.setInfected(true);
        session.setInfectUsed(true);
        session.setInfectedUserId(targetUserId);

        // Notify PRIVATE cho Infected (chỉ target mới thấy)
        Map<String, Object> infectionNotif = new HashMap<>();
        infectionNotif.put("type", "INFECTED");
        infectionNotif.put("spy_keyword", session.getSpyKeyword());
        infectionNotif.put("win_condition", "Spy thắng (Civilians còn 1) VÀ bạn vẫn còn sống");
        infectionNotif.put("message", "Bạn đã bị Tha hóa!");

        messagingTemplate.convertAndSendToUser(
                target.getUsername(),
                "/queue/infection",
                infectionNotif
        );

        // Chuyển sang DESCRIBING để tiếp tục round
        moveToDescribing(session);

        return Map.of("infected", true, "target", target.getDisplayName());
    }

    // =========================================================
    // SECTION 4: VOTE PROCESSING & WIN CONDITIONS
    // =========================================================

    private void processVoteResult(GameSession session) {
        String eliminatedId = voteManager.processVotes(session);

        if (eliminatedId == null) {
            // Hòa → Sudden Death
            stateMachine.transition(session, GameState.VOTE_TIE);
            broadcastPhase(session);
        } else {
            eliminatePlayer(session, eliminatedId);
        }
    }

    private void eliminatePlayer(GameSession session, String userId) {
        Player player = session.getPlayer(userId);
        if (player != null) {
            player.setAlive(false);
            player.setEliminatedRound(session.getCurrentRound());
            session.setEliminatedUserId(userId);
        }

        stateMachine.transition(session, GameState.ROUND_RESULT);
        broadcastRoundResult(session);
        checkWinCondition(session);
    }

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

        // Civilians còn lại = 1 → Spy thắng (deadlock)
        long aliveNonSpy = alivePlayers.stream().filter(p -> !p.getUserId().equals(session.getSpyUserId())).count();
        if (aliveNonSpy <= 1) {
            // Infected thắng cùng nếu còn sống
            session.setWinnerRole(WinnerRole.spy);
            stateMachine.transition(session, GameState.GAME_OVER);
            broadcastGameOver(session);
            return;
        }

        // AI vừa bị loại → trigger Infection phase
        String eliminatedId = session.getEliminatedUserId();
        Player eliminated = session.getPlayer(eliminatedId);
        if (eliminated != null && eliminated.isAi()) {
            handleAiEliminated(session);
            return;
        }

        startNextRound(session);
    }

    // Xử lý khi AI bị loại → kích hoạt Infection nếu đủ điều kiện
    private void handleAiEliminated(GameSession session) {
        boolean canInfect = !session.isFakeMessageUsed() && !session.isInfectUsed();

        // Round 1: luôn được tự động tha hóa
        // Round 2+: phải đã check role đúng trong round này
        boolean roleCheckedThisRound = session.isRoleCheckCorrect()
                && session.getRoleCheckRound() == session.getCurrentRound();

        boolean autoInfect = canInfect && (session.getCurrentRound() == 1 || roleCheckedThisRound);

        if (autoInfect) {
            // Chuyển sang phase INFECTION để Spy chọn target
            stateMachine.transition(session, GameState.INFECTION);
            broadcastPhase(session);

            // Notify private cho Spy: danh sách civilian còn sống để chọn
            Map<String, Object> abilityNotif = new HashMap<>();
            abilityNotif.put("type", "INFECT_AVAILABLE");
            abilityNotif.put("message", "AI đã bị loại! Chọn 1 Civilian để Tha hóa");
            abilityNotif.put("alive_civilians", getAliveCivilianList(session));

            messagingTemplate.convertAndSendToUser(
                    getSpyPlayer(session).getUsername(),
                    "/queue/ability",
                    abilityNotif
            );
        } else {
            // Không đủ điều kiện → tiếp tục round mới bình thường
            startNextRound(session);
        }
    }

    // =========================================================
    // SECTION 5: ROUND & PHASE TRANSITIONS
    // =========================================================

    private void startNextRound(GameSession session) {
        session.setCurrentRound(session.getCurrentRound() + 1);
        // Reset role check flag cho round mới
        session.setRoleCheckCorrect(false);
        session.setRoleCheckRound(0);
        session.setAbilityType(null);

        // Round 2+: vào ROLE_CHECK trước
        if (session.getCurrentRound() >= 2) {
            // Round 3+: nếu AI còn sống → auto unlock fake_message cho Spy (không cần check role)
            if (session.getCurrentRound() >= 3 && isAiAlive(session)) {
                autoUnlockFakeMessageRound3(session);
            }
            stateMachine.transition(session, GameState.ROLE_CHECK);
            session.setPhaseStartTime(LocalDateTime.now());
            session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.ROLE_CHECK_DURATION));
            timerService.startRoleCheckTimer(session.getMatchId());
        } else {
            moveToDescribing(session);
        }

        broadcastPhase(session);
    }

    // Auto-unlock fake_message từ Round 3 nếu AI còn sống
    private void autoUnlockFakeMessageRound3(GameSession session) {
        if (!session.isFakeMessageUsed()) {
            session.setRoleCheckCorrect(true);
            session.setRoleCheckRound(session.getCurrentRound());
            session.setAbilityType(SpyAbility.fake_message);

            // Notify Spy
            Map<String, Object> notif = new HashMap<>();
            notif.put("type", "FAKE_MESSAGE_AUTO_UNLOCKED");
            notif.put("message", "Round 3+: Bạn có thể dùng tin nhắn giả mạo AI!");

            messagingTemplate.convertAndSendToUser(
                    getSpyPlayer(session).getUsername(),
                    "/queue/ability",
                    notif
            );
        }
    }

    // Callbacks từ TimerService
    public void onRoleCheckPhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.ROLE_CHECK) {
            moveToDescribing(session);
        }
    }

    public void onDescribePhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.DESCRIBING) {
            moveToDiscussing(session);
        }
    }

    public void onDiscussPhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.DISCUSSING) {
            moveToVoting(session);
        }
    }

    public void onVotePhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.VOTING) {
            processVoteResult(session);
        }
    }

    private void moveToDescribing(GameSession session) {
        stateMachine.transition(session, GameState.DESCRIBING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.DESCRIBE_DURATION));
        timerService.startDescribeTimer(session.getMatchId());
        broadcastPhase(session);
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

    // =========================================================
    // SECTION 6: STATE & RESULT QUERIES
    // =========================================================

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
            state.put("your_keyword", player.getRole() == PlayerRole.spy
                    ? session.getSpyKeyword()
                    : session.getCivilianKeyword());
        }

        return state;
    }

    public GameSession getSession(String matchId) {
        GameSession session = gameSessions.get(matchId);
        if (session == null) throw new RuntimeException("Game session not found: " + matchId);
        return session;
    }

    // =========================================================
    // SECTION 7: BROADCAST HELPERS
    // =========================================================

    private void broadcastRoles(GameSession session) {
        for (Player player : session.getPlayers()) {
            Map<String, Object> roleMsg = new HashMap<>();
            roleMsg.put("match_id", session.getMatchId());
            roleMsg.put("round", session.getCurrentRound());
            roleMsg.put("role", player.getRole().toString());
            roleMsg.put("color", player.getColor().toString());
            roleMsg.put("your_keyword", player.getRole() == PlayerRole.spy
                    ? session.getSpyKeyword()
                    : session.getCivilianKeyword());

            messagingTemplate.convertAndSendToUser(player.getUserId(), "/queue/role", roleMsg);
        }
    }

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

        messagingTemplate.convertAndSend("/topic/game/" + session.getMatchId(), (Object) phaseMsg);
    }

    private void broadcastDescriptions(GameSession session) {
        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/descriptions",
                (Object) session.getCurrentRoundDescriptions()
        );
    }

    private void broadcastVoteCounts(GameSession session) {
        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/votes",
                (Object) voteManager.getVoteCounts(session)
        );
    }

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

    private void broadcastGameOver(GameSession session) {
        Map<String, Object> gameOver = new HashMap<>();
        gameOver.put("match_id", session.getMatchId());
        gameOver.put("winner_role", session.getWinnerRole().toString());
        gameOver.put("spy_user_id", session.getSpyUserId());
        gameOver.put("civilian_keyword", session.getCivilianKeyword());
        gameOver.put("spy_keyword", session.getSpyKeyword());

        // Thêm thông tin infected nếu có
        if (session.getInfectedUserId() != null) {
            Player infected = session.getPlayer(session.getInfectedUserId());
            if (infected != null) {
                boolean infectedWins = session.getWinnerRole() == WinnerRole.spy && infected.isAlive();
                gameOver.put("infected_user_id", session.getInfectedUserId());
                gameOver.put("infected_wins", infectedWins);
            }
        }

        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/game-over",
                (Object) gameOver
        );
    }

    private void notifySpyAbilityUnlocked(GameSession session) {
        String abilityType = session.getAbilityType() != null
                ? session.getAbilityType().toString()
                : null;

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "ABILITY_UNLOCKED");
        notif.put("ability", abilityType);
        notif.put("message", abilityType != null ? "Năng lực đã được mở khóa!" : "Không có năng lực khả dụng");

        messagingTemplate.convertAndSendToUser(
                getSpyPlayer(session).getUsername(),
                "/queue/ability",
                notif
        );
    }

    // =========================================================
    // SECTION 8: PRIVATE HELPERS
    // =========================================================

    private Player getAlivePlayer(GameSession session, String userId) {
        Player player = session.getPlayer(userId);
        if (player == null || !player.isAlive()) {
            throw new RuntimeException("Player not found or eliminated");
        }
        return player;
    }

    private Player getAiPlayer(GameSession session) {
        return session.getPlayers().stream()
                .filter(p -> p.isAi() && p.isAlive())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("AI player not found"));
    }

    private Player getSpyPlayer(GameSession session) {
        return session.getPlayers().stream()
                .filter(p -> p.getUserId().equals(session.getSpyUserId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Spy not found"));
    }

    private boolean isAiAlive(GameSession session) {
        return session.getPlayers().stream().anyMatch(p -> p.isAi() && p.isAlive());
    }

    private boolean allPlayersDescribed(GameSession session) {
        Map<String, String> descriptions = session.getCurrentRoundDescriptions();
        return session.getAlivePlayers().stream()
                .allMatch(p -> descriptions.containsKey(p.getUserId()));
    }

    private List<Map<String, Object>> getAliveCivilianList(GameSession session) {
        return session.getAlivePlayers().stream()
                .filter(p -> !p.getUserId().equals(session.getSpyUserId()))
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("user_id", p.getUserId());
                    m.put("display_name", p.getDisplayName());
                    m.put("color", p.getColor().toString());
                    return m;
                }).toList();
    }

    private Map<String, Object> buildRoleCheckResponse(GameSession session) {
        String ability = session.getAbilityType() != null ? session.getAbilityType().toString() : null;
        return Map.of(
                "correct", true,
                "ability_available", ability != null ? ability : "none",
                "message", ability != null ? "Năng lực đã được mở khóa!" : "Không có năng lực khả dụng"
        );
    }

    // =========================================================
    // DEBUG ONLY (chỉ hoạt động ở profile dev)
    // =========================================================

    @Profile("dev")
    public void setGameState(String matchId, GameState newState) {
        GameSession session = getSession(matchId);
        timerService.cancelTimer(matchId);
        stateMachine.transition(session, newState);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(300));
        broadcastPhase(session);
    }
}