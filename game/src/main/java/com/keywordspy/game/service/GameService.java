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

    // TODO T-056: Thay ConcurrentHashMap bằng RedisTemplate khi làm Sprint 6
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
    @Autowired private EconomyService economyService;

    // =========================================================
    // SECTION 1: GAME SETUP
    // =========================================================

    public GameSession startGame(String roomId, String hostUserId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getHostId().equals(hostUserId))
            throw new RuntimeException("Only host can start the game");
        if (room.getCurrentPlayers() < 2)
            throw new RuntimeException("Need at least 2 players to start");

        // --- ECONOMY SYSTEM: Thu phí vào cửa ---
        List<RoomPlayer> roomPlayers = roomPlayerRepository.findByRoomId(room.getId());
        for (RoomPlayer rp : roomPlayers) {
            economyService.deductEntryFee(rp.getUserId(), 100);
        }
        // ---------------------------------------

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

        List<Player> humanPlayers = players.stream().filter(p -> !p.isAi()).toList();
        Player spy = humanPlayers.get(new Random().nextInt(humanPlayers.size()));
        spy.setRole(PlayerRole.spy);
        session.setSpyUserId(spy.getUserId());
        savedMatch.setSpyUserId(spy.getUserId());
        matchRepository.save(savedMatch);

        gameSessions.put(savedMatch.getId(), session);

        stateMachine.transition(session, GameState.ROLE_ASSIGN);
        broadcastRoles(session);

        // Vòng 1 bắt đầu thẳng vào DESCRIBING
        moveToDescribing(session);

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

    public void submitDescription(String matchId, String userId, String content) {
        GameSession session = getSession(matchId);
        if (session.getState() != GameState.DESCRIBING)
            throw new RuntimeException("Not in describing phase");

        Player player = getAlivePlayer(session, userId);

        String[] words = content.trim().split("\\s+");
        if (words.length < 5 || words.length > 20)
            throw new RuntimeException("Description must be 5-20 words");

        session.getDescriptions()
                .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>())
                .put(userId, content);

        broadcastDescriptions(session);

        if (allPlayersDescribed(session)) {
            timerService.cancelTimer(matchId);
            moveToDiscussing(session);
        }
    }

    public void submitChat(String matchId, String userId, String content) {
        GameSession session = getSession(matchId);
        if (session.getState() != GameState.DISCUSSING)
            throw new RuntimeException("Not in discussing phase");

        Player player = getAlivePlayer(session, userId);

        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("user_id", userId);
        chatMsg.put("display_name", player.getDisplayName());
        chatMsg.put("color", player.getColor().toString());
        chatMsg.put("content", content);
        chatMsg.put("sent_at", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/game/" + matchId + "/chat", (Object) chatMsg);
    }

    public void submitVote(String matchId, String voterId, String targetId) {
        GameSession session = getSession(matchId);
        if (session.getState() != GameState.VOTING)
            throw new RuntimeException("Not in voting phase");

        Player voter = getAlivePlayer(session, voterId);
        Player target = session.getPlayer(targetId);
        if (target == null || !target.isAlive())
            throw new RuntimeException("Target not found or eliminated");
        if (voterId.equals(targetId))
            throw new RuntimeException("Cannot vote for yourself");

        Map<String, String> currentVotes = session.getVotes()
                .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>());
        if (currentVotes.containsKey(voterId))
            throw new RuntimeException("Already voted");

        currentVotes.put(voterId, targetId);
        broadcastVoteCounts(session);

        if (voteManager.allVoted(session)) {
            timerService.cancelTimer(matchId);
            processVoteResult(session);
        }
    }

    // =========================================================
    // SECTION 3: VÒNG ĐOÁN VAI TRÒ
    // =========================================================

    /**
     * Tất cả 6 người đều gọi endpoint này trong phase ROLE_CHECK (20s đoán).
     * Mỗi người gửi 1 lần, không được gửi lại.
     * guessedRole: "spy" hoặc "civilian"
     */
    public Map<String, Object> submitRoleGuess(String matchId, String userId, String guessedRole) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.ROLE_CHECK)
            throw new RuntimeException("Không phải phase Đoán Vai Trò");
        if (session.isRoleCheckDone())
            throw new RuntimeException("Vòng Đoán Vai Trò đã kết thúc");
        if (session.hasSubmittedRoleGuess(userId))
            throw new RuntimeException("Bạn đã đoán rồi");

        Player player = getAlivePlayer(session, userId);
        boolean isSpy = userId.equals(session.getSpyUserId());

        // Kiểm tra đoán đúng hay sai
        boolean guessedSpy = "spy".equalsIgnoreCase(guessedRole);
        boolean correct = (isSpy && guessedSpy) || (!isSpy && !guessedSpy);

        // Lưu kết quả đoán của người này
        session.getRoleCheckResults().put(userId, correct);

        // Nếu tất cả đã đoán → chuyển sang phase hiện kết quả
        if (session.allPlayersGuessed()) {
            timerService.cancelTimer(matchId);
            moveToRoleCheckResult(session);
        }

        return Map.of("submitted", true, "message", "Đã ghi nhận lựa chọn của bạn");
    }

    /**
     * Gửi kết quả cá nhân cho từng người qua WebSocket private.
     * Được gọi khi chuyển sang phase ROLE_CHECK_RESULT.
     * Civilian chỉ thấy kết quả của mình.
     * Spy thấy kết quả + được chọn dùng hay không dùng khả năng.
     */
    private void broadcastRoleCheckResults(GameSession session) {
        boolean aiAlive = isAiAlive(session);

        for (Player player : session.getAlivePlayers()) {
            String uid = player.getUserId();
            boolean isSpy = uid.equals(session.getSpyUserId());
            boolean correct = session.getRoleCheckResults().getOrDefault(uid, false);

            Map<String, Object> result = new HashMap<>();
            result.put("type", "ROLE_CHECK_RESULT");
            result.put("correct", correct);

            if (!isSpy) {
                // === CIVILIAN ===
            if (correct) {
                result.put("message", "Chính xác! Bạn là Dân Thường");
                result.put("reward_coins", true);
                
                // --- ECONOMY SYSTEM: Thưởng đoán đúng vai ---
                economyService.addReward(uid, 20, Transaction.TransactionType.GUESS_BONUS, "Đoán đúng vai Civilian", true);
                // --------------------------------------------
            } else {
                    result.put("message", "Sai rồi! Không nhận được xu");
                    result.put("reward_coins", false);
                }
                result.put("ability_available", null);

            } else {
                // === SPY ===
                if (!correct) {
                    // Spy đoán sai → không biết mình là Spy, mất cả 2 khả năng
                    result.put("message", "Sai rồi! Không nhận được xu");
                    result.put("reward_coins", false);
                    result.put("ability_available", null);
                    // spyKnowsRole giữ false
                } else {
                    // Spy đoán đúng → biết mình là Spy
                    session.setSpyKnowsRole(true);
                    result.put("message", "Chính xác! Bạn là Gián Điệp");
                    result.put("reward_coins", true); // Spy nhận xu khi đoán đúng

                    // --- ECONOMY SYSTEM: Thưởng Spy đoán đúng vai ---
                    economyService.addReward(uid, 50, Transaction.TransactionType.GUESS_BONUS, "Spy đoán đúng vai", true);
                    // ------------------------------------------------

                    if (aiAlive) {
                        // AI còn sống → offer Giả Mạo AI
                        result.put("ability_available", "fake_message");
                        result.put("ability_description", "Giả Mạo AI: mỗi vòng gửi 1 tin nhắn thay AI");
                        result.put("ability_tradeoff", "Nếu chọn dùng, bạn sẽ mất quyền Tha Hóa vĩnh viễn");
                    } else {
                        // AI đã chết → offer Tha Hóa
                        result.put("ability_available", "infection");
                        result.put("ability_description", "Tha Hóa: chọn 1 Dân Thường làm đồng minh bí mật");
                        result.put("ability_tradeoff", "Phải chọn người ngay bây giờ, hết giờ sẽ mất quyền");
                        result.put("alive_civilians", getAliveCivilianList(session));
                    }
                }
            }

            messagingTemplate.convertAndSendToUser(
                    player.getUsername(),
                    "/queue/role-check-result",
                    result
            );
        }
    }

    /**
     * Spy xác nhận dùng hay không dùng khả năng — gọi trong phase ROLE_CHECK_RESULT.
     * useAbility = true → kích hoạt khả năng
     * useAbility = false → từ chối, biết mình là Spy nhưng không có khả năng
     */
    public Map<String, Object> confirmSpyAbility(String matchId, String userId, boolean useAbility) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.ROLE_CHECK_RESULT)
            throw new RuntimeException("Không phải phase kết quả Đoán Vai");
        if (!userId.equals(session.getSpyUserId()))
            throw new RuntimeException("Chỉ Gián Điệp mới có thể xác nhận khả năng");
        if (!session.isSpyKnowsRole())
            throw new RuntimeException("Gián Điệp chưa đoán đúng vai trò");
        if (session.getAbilityType() != null || session.isSpyAbilityDeclined())
            throw new RuntimeException("Đã xác nhận rồi");

        if (!useAbility) {
            // Spy từ chối — không có khả năng cả ván
            session.setSpyAbilityDeclined(true);
            return Map.of("confirmed", true, "ability", "none",
                    "message", "Bạn chọn không dùng khả năng. Chúc may mắn!");
        }

        // Spy đồng ý dùng
        boolean aiAlive = isAiAlive(session);
        if (aiAlive) {
            session.setAbilityType(SpyAbility.fake_message);
            return Map.of("confirmed", true, "ability", "fake_message",
                    "message", "Đã kích hoạt Giả Mạo AI! Dùng được mỗi vòng từ vòng 2.");
        } else {
            // Tha Hóa — Spy phải chọn target ngay (trong 20s này)
            session.setAbilityType(SpyAbility.infection);
            return Map.of("confirmed", true, "ability", "infection",
                    "message", "Đã kích hoạt Tha Hóa! Hãy chọn người bạn muốn tha hóa ngay.");
        }
    }

    // =========================================================
    // SECTION 4: SPY ABILITIES
    // =========================================================

    /**
     * Giả Mạo AI — dùng trong phase DESCRIBING hoặc DISCUSSING.
     * Được dùng mỗi vòng 1 lần, miễn AI còn sống.
     * Reset fakeMessageUsedThisRound ở đầu mỗi vòng mới.
     */
    public Map<String, Object> useFakeMessageAbility(String matchId, String userId, String content) {
        GameSession session = getSession(matchId);

        if (session.getState() != GameState.DESCRIBING && session.getState() != GameState.DISCUSSING)
            throw new RuntimeException("Chỉ dùng được trong phase Mô Tả hoặc Thảo Luận");
        if (!userId.equals(session.getSpyUserId()))
            throw new RuntimeException("Chỉ Gián Điệp mới có thể dùng khả năng này");
        if (session.getAbilityType() != SpyAbility.fake_message)
            throw new RuntimeException("Khả năng Giả Mạo AI chưa được kích hoạt");
        if (!isAiAlive(session))
            throw new RuntimeException("AI đã bị loại, không thể giả mạo");
        if (session.isFakeMessageUsedThisRound())
            throw new RuntimeException("Đã dùng Giả Mạo AI trong vòng này rồi");

        String[] words = content.trim().split("\\s+");
        if (words.length < 5 || words.length > 20)
            throw new RuntimeException("Nội dung phải từ 5-20 từ");

        Player aiPlayer = getAiPlayer(session);

        session.setFakeMessageUsedThisRound(true);
        session.getFakeDescriptions().put(session.getCurrentRound(), content);

        // --- ECONOMY SYSTEM: Thưởng kỹ năng Spy ---
        economyService.addReward(userId, 30, Transaction.TransactionType.SKILL_BONUS, "Kỹ năng Giả Mạo AI", true);
        // ------------------------------------------

        // Broadcast như tin của AI — FE không biết là fake
        Map<String, Object> fakeMsg = new HashMap<>();
        fakeMsg.put("user_id", aiPlayer.getUserId());
        fakeMsg.put("display_name", aiPlayer.getDisplayName());
        fakeMsg.put("color", aiPlayer.getColor().toString());
        fakeMsg.put("content", content);
        fakeMsg.put("sent_at", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/game/" + matchId + "/chat", (Object) fakeMsg);

        return Map.of("sent", true, "displayed_as", aiPlayer.getDisplayName());
    }

    /**
     * Tha Hóa — gọi trong phase ROLE_CHECK_RESULT (trong 20s kết quả).
     * Nếu hết giờ mà chưa gọi → mất quyền (xử lý trong onRoleCheckResultPhaseEnd).
     */
    public Map<String, Object> infectPlayer(String matchId, String spyUserId, String targetUserId) {
        GameSession session = getSession(matchId);

        if (!spyUserId.equals(session.getSpyUserId()))
            throw new RuntimeException("Chỉ Gián Điệp mới có thể Tha Hóa");
        if (session.getAbilityType() != SpyAbility.infection)
            throw new RuntimeException("Khả năng Tha Hóa chưa được kích hoạt");
        if (session.isInfectUsed())
            throw new RuntimeException("Đã Tha Hóa rồi");
        if (session.getState() != GameState.ROLE_CHECK_RESULT)
            throw new RuntimeException("Chỉ có thể Tha Hóa trong phase kết quả Đoán Vai");

        Player target = session.getPlayer(targetUserId);
        if (target == null || !target.isAlive())
            throw new RuntimeException("Người chơi không tồn tại hoặc đã bị loại");
        if (targetUserId.equals(spyUserId))
            throw new RuntimeException("Không thể Tha Hóa chính mình");

        target.setInfected(true);
        session.setInfectUsed(true);
        session.setInfectedUserId(targetUserId);

        // Notify private cho Infected
        Map<String, Object> infectionNotif = new HashMap<>();
        infectionNotif.put("type", "INFECTED");
        infectionNotif.put("spy_keyword", session.getSpyKeyword());
        infectionNotif.put("win_condition", "Gián Điệp thắng (Dân Thường còn 1) VÀ bạn vẫn còn sống");
        infectionNotif.put("message", "Bạn đã bị Tha Hóa!");
        messagingTemplate.convertAndSendToUser(
                target.getUsername(), "/queue/infection", infectionNotif);

        return Map.of("infected", true, "target", target.getDisplayName());
    }

    // =========================================================
    // SECTION 5: VOTE PROCESSING & WIN CONDITIONS
    // =========================================================

    private void processVoteResult(GameSession session) {
        String eliminatedId = voteManager.processVotes(session);
        if (eliminatedId == null) {
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

        // Spy bị loại → Civilians thắng
        if (spy != null && !spy.isAlive()) {
            session.setWinnerRole(WinnerRole.civilians);
            stateMachine.transition(session, GameState.GAME_OVER);
            broadcastGameOver(session);
            return;
        }

        // Civilians (không kể Spy) còn 1 → Spy thắng
        long aliveNonSpy = session.getAlivePlayers().stream()
                .filter(p -> !p.getUserId().equals(session.getSpyUserId()))
                .count();
        if (aliveNonSpy <= 1) {
            session.setWinnerRole(WinnerRole.spy);
            stateMachine.transition(session, GameState.GAME_OVER);
            broadcastGameOver(session);
            return;
        }

        startNextRound(session);
    }

    // =========================================================
    // SECTION 6: ROUND & PHASE TRANSITIONS
    // =========================================================

    private void startNextRound(GameSession session) {
        int nextRound = session.getCurrentRound() + 1;
        session.setCurrentRound(nextRound);
        session.setEliminatedUserId(null); // reset kết quả round trước

        // Reset fake message cho vòng mới (Spy được dùng lại)
        session.setFakeMessageUsedThisRound(false);

        if (nextRound == 2 && !session.isRoleCheckDone()) {
            // Sau Vòng 1 → vào Vòng Đoán Vai Trò (1 lần duy nhất)
            moveToRoleCheck(session);
        } else {
            // Vòng 3 trở đi → thẳng vào DESCRIBING
            moveToDescribing(session);
        }
    }

    private void moveToRoleCheck(GameSession session) {
        stateMachine.transition(session, GameState.ROLE_CHECK);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.ROLE_CHECK_DURATION));
        timerService.startRoleCheckTimer(session.getMatchId());
        broadcastPhase(session);
    }

    private void moveToRoleCheckResult(GameSession session) {
        stateMachine.transition(session, GameState.ROLE_CHECK_RESULT);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.ROLE_CHECK_RESULT_DURATION));
        timerService.startRoleCheckResultTimer(session.getMatchId());

        // Gửi kết quả cá nhân cho từng người
        broadcastRoleCheckResults(session);
        broadcastPhase(session);
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
    // SECTION 7: TIMER CALLBACKS
    // =========================================================

    public void onDescribePhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.DESCRIBING)
            moveToDiscussing(session);
    }

    public void onDiscussPhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.DISCUSSING)
            moveToVoting(session);
    }

    public void onVotePhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.VOTING)
            processVoteResult(session);
    }

    /**
     * Hết 20s đoán vai — ai chưa đoán xem như bỏ qua (không nhận lợi ích).
     * Chuyển sang phase kết quả.
     */
    public void onRoleCheckPhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.ROLE_CHECK) {
            // Người chưa đoán → ghi nhận là sai (không nhận lợi ích)
            for (Player player : session.getAlivePlayers()) {
                if (!session.hasSubmittedRoleGuess(player.getUserId())) {
                    session.getRoleCheckResults().put(player.getUserId(), false);
                }
            }
            moveToRoleCheckResult(session);
        }
    }

    /**
     * Hết 20s kết quả — Spy chưa chọn người Tha Hóa → mất quyền.
     * Đánh dấu roleCheckDone và chuyển sang Vòng 2.
     */
    public void onRoleCheckResultPhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.ROLE_CHECK_RESULT) {

            // Nếu Spy có Tha Hóa nhưng chưa chọn người → mất quyền
            if (session.getAbilityType() == SpyAbility.infection && !session.isInfectUsed()) {
                session.setAbilityType(null);
                session.setSpyAbilityDeclined(true);

                // Notify Spy: hết giờ, mất quyền
                messagingTemplate.convertAndSendToUser(
                        getSpyPlayer(session).getUsername(),
                        "/queue/ability",
                        Map.of("type", "INFECT_EXPIRED",
                               "message", "Hết giờ! Bạn đã mất quyền Tha Hóa."));
            }

            session.setRoleCheckDone(true);
            // Chuyển thẳng vào Vòng 2
            moveToDescribing(session);
        }
    }

    // =========================================================
    // SECTION 8: STATE & RESULT QUERIES
    // =========================================================

    public Map<String, Object> getGameState(String matchId, String userId) {
        GameSession session = getSession(matchId);
        Player player = session.getPlayer(userId);

        Map<String, Object> state = new HashMap<>();
        state.put("match_id", matchId);
        state.put("round", session.getCurrentRound());
        state.put("phase", session.getState().toString());
        if (session.getPhaseEndTime() != null)
            state.put("phase_end_at", session.getPhaseEndTime().toString());

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
        if (session == null)
            throw new RuntimeException("Game session not found: " + matchId);
        return session;
    }

    // =========================================================
    // SECTION 9: BROADCAST HELPERS
    // =========================================================

    private void broadcastRoles(GameSession session) {
        for (Player player : session.getPlayers()) {
            Map<String, Object> roleMsg = new HashMap<>();
            roleMsg.put("match_id", session.getMatchId());
            roleMsg.put("round", session.getCurrentRound());
            roleMsg.put("role", player.getRole().toString());
            roleMsg.put("color", player.getColor().toString());
            // Tất cả nhận keyword nhưng chưa biết mình là vai gì
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
        if (session.getPhaseEndTime() != null)
            phaseMsg.put("phase_end_at", session.getPhaseEndTime().toString());
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
                (Object) session.getCurrentRoundDescriptions());
    }

    private void broadcastVoteCounts(GameSession session) {
        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/votes",
                (Object) voteManager.getVoteCounts(session));
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
                "/topic/game/" + session.getMatchId() + "/round-result", (Object) result);
    }

    private void broadcastGameOver(GameSession session) {
        Map<String, Object> gameOver = new HashMap<>();
        gameOver.put("match_id", session.getMatchId());
        gameOver.put("winner_role", session.getWinnerRole().toString());
        gameOver.put("spy_user_id", session.getSpyUserId());
        gameOver.put("civilian_keyword", session.getCivilianKeyword());
        gameOver.put("spy_keyword", session.getSpyKeyword());

        // --- ECONOMY SYSTEM: Phát thưởng ---
        processEndGameRewards(session);
        // ------------------------------------

        if (session.getInfectedUserId() != null) {
            Player infected = session.getPlayer(session.getInfectedUserId());
            if (infected != null) {
                boolean infectedWins = session.getWinnerRole() == WinnerRole.spy && infected.isAlive();
                gameOver.put("infected_user_id", session.getInfectedUserId());
                gameOver.put("infected_wins", infectedWins);
            }
        }
        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getMatchId() + "/game-over", (Object) gameOver);
    }

    // =========================================================
    // SECTION 10: PRIVATE HELPERS
    // =========================================================

    private Player getAlivePlayer(GameSession session, String userId) {
        Player player = session.getPlayer(userId);
        if (player == null || !player.isAlive())
            throw new RuntimeException("Player not found or eliminated");
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

    // --- ECONOMY SYSTEM: Logic tính toán thưởng cuối ván ---
    private void processEndGameRewards(GameSession session) {
        String matchId = session.getMatchId();
        WinnerRole winner = session.getWinnerRole();

        if (winner == WinnerRole.spy) {
            // SPY THẮNG: Thưởng Spy 350, Infected 120, Last Survivor 70
            economyService.addReward(session.getSpyUserId(), 350, Transaction.TransactionType.WIN_REWARD, "Spy Thắng Ván: " + matchId, true);

            if (session.getInfectedUserId() != null) {
                Player infected = session.getPlayer(session.getInfectedUserId());
                if (infected != null && infected.isAlive()) {
                    economyService.addReward(session.getInfectedUserId(), 120, Transaction.TransactionType.WIN_REWARD, "Infected Thắng Ván: " + matchId, true);
                }
            }

            // Dân thường sống sót cuối cùng (không tính spy/infected)
            session.getAlivePlayers().stream()
                .filter(p -> !p.isAi() && !p.getUserId().equals(session.getSpyUserId()) && !p.getUserId().equals(session.getInfectedUserId()))
                .findFirst()
                .ifPresent(p -> economyService.addReward(p.getUserId(), 70, Transaction.TransactionType.WIN_REWARD, "Dân thường sống sót cuối cùng ván: " + matchId, true));

        } else if (winner == WinnerRole.civilians) {
            // DÂN THƯỜNG THẮNG: Mỗi dân thường (sống/chết) nhận 135
            session.getPlayers().stream()
                .filter(p -> !p.isAi() && p.getRole() == PlayerRole.civilian && !p.getUserId().equals(session.getInfectedUserId()))
                .forEach(p -> economyService.addReward(p.getUserId(), 135, Transaction.TransactionType.WIN_REWARD, "Dân thường Thắng Ván: " + matchId, true));
        }
    }
    // ----------------------------------------------------

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

    // =========================================================
    // DEBUG ONLY
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