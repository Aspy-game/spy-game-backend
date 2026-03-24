package com.keywordspy.game.service;

import com.keywordspy.game.manager.GameStateMachine;
import com.keywordspy.game.manager.VoteManager;
import com.keywordspy.game.model.*;
import com.keywordspy.game.model.GameSession.GameState;
import com.keywordspy.game.repository.MatchRepository;
import com.keywordspy.game.repository.RoomRepository;
import com.keywordspy.game.repository.RoomPlayerRepository;
import com.keywordspy.game.repository.MatchPlayerRepository;
import com.keywordspy.game.repository.UserStatsRepository;
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
    @Autowired private MatchPlayerRepository matchPlayerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserStatsRepository userStatsRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private EconomyService economyService;
    @Autowired private SettingsService settingsService;
    @Autowired private AiService aiService;

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

        // --- ECONOMY SYSTEM: Thu phí vào cửa (Entry Fee: 10 xu) ---
        int entryFee = 10;
        List<RoomPlayer> roomPlayers = roomPlayerRepository.findByRoomId(room.getId());
        for (RoomPlayer rp : roomPlayers) {
            economyService.deductEntryFee(rp.getUserId(), entryFee);
        }
        // ---------------------------------------------------------

        KeywordPair keyword = keywordService.getRandomKeyword();

        Match match = new Match();
        match.setRoomId(roomId);
        match.setCivilianKeyword(keyword.getCivilianKeyword());
        match.setSpyKeyword(keyword.getSpyKeyword());
        match.setStatus(MatchStatus.in_progress);
        match.setStartedAt(LocalDateTime.now());
        Match savedMatch = matchRepository.save(match);

        GameSession session = new GameSession();
        session.setMatchId(savedMatch.getId());
        session.setRoomId(roomId);
        session.setRoomCode(room.getRoomCode());
        session.setCivilianKeyword(keyword.getCivilianKeyword());
        session.setSpyKeyword(keyword.getSpyKeyword());
        session.setKeywordPairId(keyword.getId());
        session.setCurrentRound(1);

        List<Player> players = createPlayers(room, savedMatch.getId());
        session.setPlayers(players);

        List<Player> allPlayers = new ArrayList<>(players);
        Collections.shuffle(allPlayers);

        String spyUserId = null;
        
        // --- ADMIN SELECTION: Kiểm tra nếu admin đã chọn Spy ---
        if (room.getAdminSelectedSpyId() != null) {
            String selectedId = room.getAdminSelectedSpyId();
            if (players.stream().anyMatch(p -> p.getUserId().equals(selectedId))) {
                spyUserId = selectedId;
            }
        }
        
        // Nếu admin không chọn hoặc người được chọn không có trong phòng
        if (spyUserId == null) {
            // Lọc ra các player là human để gán spy (AI không làm spy)
            List<Player> humanPlayers = players.stream().filter(p -> !p.isAi()).toList();
            if (!humanPlayers.isEmpty()) {
                List<Player> shuffleHumans = new ArrayList<>(humanPlayers);
                Collections.shuffle(shuffleHumans);
                spyUserId = shuffleHumans.get(0).getUserId();
            } else {
                spyUserId = allPlayers.get(0).getUserId();
            }
        }

        final String finalSpyUserId = spyUserId;
        for (Player p : players) {
            if (p.getUserId().equals(finalSpyUserId)) {
                p.setRole(PlayerRole.spy);
            } else {
                p.setRole(PlayerRole.civilian);
            }
        }

        session.setSpyUserId(finalSpyUserId);
        savedMatch.setSpyUserId(finalSpyUserId);
        matchRepository.save(savedMatch);

        // Reset admin selection sau khi dùng
        room.setAdminSelectedSpyId(null);
        roomRepository.save(room);

        gameSessions.put(savedMatch.getId(), session);

        // --- MATCH PLAYER: Khởi tạo dữ liệu người chơi trong trận ---
        for (Player p : players) {
            if (p.isAi()) continue;
            MatchPlayer mp = new MatchPlayer();
            mp.setMatchId(savedMatch.getId());
            mp.setUserId(p.getUserId());
            mp.setColor(p.getColor());
            mp.setRole(p.getRole());
            matchPlayerRepository.save(mp);
        }
        // ---------------------------------------------------------

        // Bắt đầu phase ROLE_ASSIGN (10 giây để xem keyword)
        moveToRoleAssign(session);

        room.setStatus(RoomStatus.in_game);
        roomRepository.save(room);

        // Thông báo GAME_START cho lobby/phòng
        Map<String, Object> gameStart = new HashMap<>();
        gameStart.put("type", "GAME_START");
        gameStart.put("room_id", room.getId());
        gameStart.put("match_id", savedMatch.getId());
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), (Object) gameStart);

        return session;
    }

    private void moveToRoleAssign(GameSession session) {
        stateMachine.transition(session, GameState.ROLE_ASSIGN);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(10)); // 10s xem vai

        broadcastRoles(session);
        broadcastPhase(session);

        // Hẹn giờ kết thúc ROLE_ASSIGN để vào DESCRIBING
        timerService.startTimer(session.getMatchId(), 10, () -> {
            GameSession s = getSession(session.getMatchId());
            if (s.getState() == GameState.ROLE_ASSIGN) {
                moveToDescribing(s);
            }
        });
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

        // LUÔN LUÔN THÊM ĐÚNG 1 AI (HIGHLIGHT CỦA GAME)
        Player aiPlayer = new Player();
        aiPlayer.setUserId("ai_official");
        aiPlayer.setUsername("AI KeywordSpy");
        aiPlayer.setDisplayName("AI KeywordSpy");
        aiPlayer.setColor(colors.isEmpty() ? PlayerColor.red : colors.remove(0));
        aiPlayer.setRole(PlayerRole.ai_civilian);
        aiPlayer.setAi(true);
        aiPlayer.setAlive(true);
        players.add(aiPlayer);

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
        // Giảm yêu cầu độ dài để người chơi thoải mái test
        if (words.length < 1 || words.length > 30)
            throw new RuntimeException("Mô tả phải từ 1-30 từ");

        session.getDescriptions()
                .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>())
                .put(userId, content);

        broadcastDescriptions(session);

        // KÍCH HOẠT AI MÔ TẢ (Nếu chưa bị thao túng)
        if (session.getAbilityType() != SpyAbility.fake_message && isAiAlive(session)) {
            Player ai = getAiPlayer(session);
            Map<String, String> roundDescs = session.getCurrentRoundDescriptions();
            if (!roundDescs.containsKey(ai.getUserId())) {
                // AI sẽ mô tả dựa trên mô tả của người vừa nhắn (content)
                autoDescribeForAi(session, content);
            }
        }

        // if (allPlayersDescribed(session)) {
        //     timerService.cancelTimer(matchId);
        //     moveToDiscussing(session);
        // }
    }

    public void submitChat(String matchId, String userId, String content) {
        GameSession session = getSession(matchId);
        if (session.getState() != GameState.DISCUSSING)
            throw new RuntimeException("Not in discussing phase");

        Player player = getAlivePlayer(session, userId);

        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("user_id", userId);
        String name = (session.getState() != null && session.getState() != GameState.GAME_OVER) 
                ? getAnonymousName(player) 
                : (player.getDisplayName() != null ? player.getDisplayName() : player.getUsername());
        chatMsg.put("display_name", name);
        chatMsg.put("color", player.getColor() != null ? player.getColor().toString() : "red");
        chatMsg.put("content", content);
        chatMsg.put("sender", name);
        chatMsg.put("sent_at", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/match/" + matchId + "/chat", (Object) chatMsg);

        // KÍCH HOẠT AI THẢO LUẬN (Nếu chưa bị thao túng và AI chưa chat vòng này)
        // Trong phase Thảo luận, AI có thể chat nhiều lần, nhưng lần đầu tiên cần context
        if (session.getAbilityType() != SpyAbility.fake_message && isAiAlive(session)) {
            // Giả sử AI chỉ tự động thảo luận 1 lần ngay sau tin nhắn đầu tiên của human
            // Hoặc có thể thêm logic xác suất/thời gian ở đây
            autoDiscussForAi(session, content);
        }
    }

    public void submitVote(String matchId, String voterId, String targetId) {
        GameSession session = getSession(matchId);
        if (session.getState() != GameState.VOTING) {
            return; // Tránh lỗi khi phase đã chuyển đổi nhanh
        }
        
        // Ngăn chặn người đã chết vote
        getAlivePlayer(session, voterId);

        Map<String, String> currentVotes = session.getVotes()
                .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>());

        if (currentVotes.containsKey(voterId)) {
            return; // Đã vote rồi thì bỏ qua
        }

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
            if (player.isAi()) continue;

            String uid = player.getUserId();
            boolean isSpy = uid.equals(session.getSpyUserId());
            boolean correct = session.getRoleCheckResults().getOrDefault(uid, false);

            Map<String, Object> result = new HashMap<>();
            result.put("type", "ROLE_CHECK_RESULT");
            result.put("correct", correct);
            
            // Xác định vai trò hiển thị (Civilian, Spy, Infected)
            String displayRole = player.getRole().toString();
            if (player.isInfected()) {
                displayRole = "infected";
            }
            result.put("actual_role", displayRole);

            if (!isSpy) {
                // === CIVILIAN & INFECTED ===
                if (correct) {
                    result.put("message", player.isInfected() ? "Chính xác! Bạn là Kẻ Bị Tha Hóa" : "Chính xác! Bạn là Dân Thường");
                    result.put("reward_coins", true);
                    
                    int reward = player.isInfected() ? session.getRewardInfectedGuess() : session.getRewardCivilianGuess();
                    result.put("reward_amount", reward);
                    
                    // --- ECONOMY SYSTEM: Thưởng đoán đúng vai ---
                    economyService.addReward(uid, reward, Transaction.TransactionType.GUESS_BONUS, "Đoán đúng vai " + displayRole, true);
                    // --------------------------------------------
                } else {
                    result.put("message", "Sai rồi! Không nhận được xu");
                    result.put("reward_coins", false);
                }
                result.put("abilities_available", null);

            } else {
                // === SPY ===
                if (correct) {
                    // Spy đoán đúng — biết mình là Spy
                    session.setSpyKnowsRole(true);
                    result.put("message", "Chính xác! Bạn là Gián Điệp");
                    result.put("reward_coins", true); 
                    result.put("reward_amount", session.getRewardSpyGuess());
                    
                    // --- ECONOMY SYSTEM: Thưởng Spy đoán đúng vai ---
                    economyService.addReward(uid, session.getRewardSpyGuess(), Transaction.TransactionType.GUESS_BONUS, "Spy đoán đúng vai", true);
                    // ------------------------------------------------

                    // LUÔN CHO PHÉP CHỌN 1 TRONG 2 KỸ NĂNG (NẾU ĐOÁN ĐÚNG)
                    result.put("abilities_available", getAvailableAbilitiesForSpy(session));
                    
                    // Gửi thêm danh sách người để Tha Hóa nếu cần
                    result.put("alive_humans", getAliveCivilianList(session));
                } else {
                    // Spy đoán sai
                    result.put("message", "Sai rồi! Bạn không nhận ra bản thân.");
                    result.put("reward_coins", false);
                    result.put("abilities_available", null);
                }
            }

            if (player.getUsername() != null) {
                messagingTemplate.convertAndSendToUser(
                        player.getUsername(),
                        "/queue/role-check-result",
                        result
                );
                
                // Gửi thêm một kênh backup để FE dễ bắt nếu kênh trên bị trùng lặp/chặn
                messagingTemplate.convertAndSendToUser(
                        player.getUsername(),
                        "/queue/role-result",
                        result
                );
            }
        }
    }

    public Map<String, Object> confirmSpyAbility(String matchId, String userId, String abilityTypeStr) {
        GameSession session = getSession(matchId);
        Player spy = session.getPlayer(userId);
        if (spy == null || spy.getRole() != PlayerRole.spy) {
            throw new RuntimeException("Chỉ Gián Điệp mới có thể xác nhận kỹ năng");
        }
        
        if (!session.isSpyKnowsRole()) {
            throw new RuntimeException("Bạn cần đoán đúng vai trò để sử dụng kỹ năng");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("confirmed", true);

        SpyAbility selectedAbility;
        try {
            selectedAbility = SpyAbility.valueOf(abilityTypeStr.toLowerCase());
        } catch (Exception e) {
            selectedAbility = SpyAbility.none;
        }

        if (selectedAbility == SpyAbility.fake_message) {
            if (!isAiAlive(session)) {
                throw new RuntimeException("AI đã bị loại, không thể dùng kỹ năng Thao túng AI");
            }
            session.setAbilityType(SpyAbility.fake_message);
            result.put("ability", "fake_message");
        } else if (selectedAbility == SpyAbility.infection) {
            session.setAbilityType(SpyAbility.infection);
            result.put("ability", "infection");
        } else {
            session.setAbilityType(SpyAbility.none);
            session.setSpyAbilityDeclined(true);
            result.put("ability", "none");
        }

        return result;
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
        if (!session.isSpyKnowsRole())
            throw new RuntimeException("Bạn cần đoán đúng vai trò để mở khóa kỹ năng");
        if (!isAiAlive(session))
            throw new RuntimeException("AI đã bị loại, không thể thao túng");

        Player aiPlayer = getAiPlayer(session);

        // Broadcast như tin của AI — FE không biết là fake
        Map<String, Object> fakeMsg = new HashMap<>();
        fakeMsg.put("match_id", matchId);
        fakeMsg.put("display_name", getAnonymousName(aiPlayer));
        fakeMsg.put("color", aiPlayer.getColor().toString());
        fakeMsg.put("content", content);
        fakeMsg.put("sent_at", LocalDateTime.now().toString());
        fakeMsg.put("is_manipulated", true); // Flag ẩn cho BE/Admin
        
        messagingTemplate.convertAndSend("/topic/match/" + matchId + "/chat", (Object) fakeMsg);

        // NẾU ĐANG TRONG PHASE MÔ TẢ, LƯU LẠI MÔ TẢ CỦA AI
        if (session.getState() == GameState.DESCRIBING) {
            session.getDescriptions()
                    .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>())
                    .put(aiPlayer.getUserId(), content);
            broadcastDescriptions(session);
        }

        // Thưởng xu cho Spy mỗi lần thao túng thành công
        economyService.addReward(userId, 30, Transaction.TransactionType.SKILL_BONUS, "Thao túng AI", true);

        return Map.of("sent", true, "message", "Đã gửi tin nhắn giả danh AI");
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

        // --- MATCH PLAYER: Cập nhật trạng thái bị tha hóa ---
        matchPlayerRepository.findByMatchId(matchId).stream()
                .filter(mp -> mp.getUserId().equals(targetUserId))
                .findFirst()
                .ifPresent(mp -> {
                    mp.setInfected(true);
                    matchPlayerRepository.save(mp);
                });
        // --------------------------------------------------

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
            // Trường hợp hòa (tie)
            stateMachine.transition(session, GameState.VOTE_TIE);
            session.setPhaseStartTime(LocalDateTime.now());
            session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.VOTE_TIE_DURATION));
            timerService.startVoteTieTimer(session.getMatchId());
            
            // Log chi tiết cho F12
            System.out.println("[GAME-LOG] Vote Tie in match: " + session.getMatchId() + ", Round: " + session.getCurrentRound());
            
            // Thông báo kết quả hòa cho FE
            broadcastRoundResult(session); 
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

            // --- MATCH PLAYER: Lưu vòng bị loại ---
            matchPlayerRepository.findByMatchId(session.getMatchId()).stream()
                    .filter(mp -> mp.getUserId().equals(userId))
                    .findFirst()
                    .ifPresent(mp -> {
                        mp.setEliminatedRound(session.getCurrentRound());
                        matchPlayerRepository.save(mp);
                    });
            // ------------------------------------
        }
        stateMachine.transition(session, GameState.ROUND_RESULT);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(TimerService.ROUND_RESULT_DURATION));
        timerService.startRoundResultTimer(session.getMatchId());
        
        broadcastRoundResult(session);
        broadcastPhase(session);
    }

    public void onRoundResultEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.ROUND_RESULT) {
            checkWinCondition(session);
        }
    }

    private void checkWinCondition(GameSession session) {
        Player spy = session.getPlayer(session.getSpyUserId());

        // Spy bị loại → Civilians thắng
        if (spy != null && !spy.isAlive()) {
            session.setWinnerRole(WinnerRole.civilians);
            stateMachine.transition(session, GameState.GAME_OVER);
            broadcastPhase(session); // Gửi tên thật cho Frontend
            broadcastGameOver(session);
            return;
        }

        // Civilians (không kể Spy) còn 1 → Spy thắng
        // Logic mới: tính cả người bị tha hóa (infected) thuộc phe Spy
        // KHÔNG tính AI vào số lượng dân thường thật sự (vì Gián điệp thao túng được AI)
        long aliveCivilians = session.getAlivePlayers().stream()
                .filter(p -> p.getRole() == PlayerRole.civilian && !p.isInfected() && !p.isAi())
                .count();
        
        if (aliveCivilians <= 1) {
            session.setWinnerRole(WinnerRole.spy);
            stateMachine.transition(session, GameState.GAME_OVER);
            broadcastPhase(session); // Gửi tên thật cho Frontend
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
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(timerService.getRoleCheckDuration()));
        timerService.startRoleCheckTimer(session.getMatchId());
        autoRoleCheckForAi(session);
        broadcastPhase(session);
    }

    private void moveToRoleCheckResult(GameSession session) {
        stateMachine.transition(session, GameState.ROLE_CHECK_RESULT);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(timerService.getRoleCheckResultDuration()));
        timerService.startRoleCheckResultTimer(session.getMatchId());

        // Gửi kết quả cá nhân cho từng người
        broadcastRoleCheckResults(session);
        broadcastPhase(session);
    }

    private void moveToDescribing(GameSession session) {
        stateMachine.transition(session, GameState.DESCRIBING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(timerService.getDescribeDuration()));
        timerService.startDescribeTimer(session.getMatchId());
        
        // Gửi phase thông báo trước
        broadcastPhase(session);
        
        // AI sẽ không tự động mô tả ngay lập tức. 
        // Nó sẽ đợi ít nhất 1 người chơi mô tả để lấy ngữ cảnh.
    }

    private void moveToDiscussing(GameSession session) {
        stateMachine.transition(session, GameState.DISCUSSING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(timerService.getDiscussDuration()));
        timerService.startDiscussTimer(session.getMatchId());
        broadcastPhase(session);
    }

    private void moveToVoting(GameSession session) {
        stateMachine.transition(session, GameState.VOTING);
        session.setPhaseStartTime(LocalDateTime.now());
        session.setPhaseEndTime(LocalDateTime.now().plusSeconds(timerService.getVoteDuration()));
        timerService.startVoteTimer(session.getMatchId());
        autoVoteForAi(session);
        broadcastPhase(session);
    }

    // =========================================================
    // SECTION 7: TIMER CALLBACKS & ADMIN ACTIONS
    // =========================================================

    public void skipPhase(String matchId) {
        GameSession session = getSession(matchId);
        timerService.cancelTimer(matchId);
         // Nếu game đã kết thúc, không làm gì cả
        if (session.getState() == GameState.GAME_OVER) {
            return;
        }

        switch (session.getState()) {
            case DESCRIBING -> onDescribePhaseEnd(matchId);
            case DISCUSSING -> onDiscussPhaseEnd(matchId);
            case VOTING -> onVotePhaseEnd(matchId);
            case VOTE_TIE -> onVoteTieEnd(matchId);
            case ROLE_CHECK -> onRoleCheckPhaseEnd(matchId);
            case ROLE_CHECK_RESULT -> onRoleCheckResultPhaseEnd(matchId);
            default -> throw new RuntimeException("Cannot skip phase in state: " + session.getState());
        }
    }

    public void onVoteTieEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.VOTE_TIE) {
            startNextRound(session);
        }
    }

    public void onDescribePhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.DESCRIBING) {
            // Điền nội dung mặc định cho người chưa mô tả
            Map<String, String> roundDesc = session.getDescriptions()
                    .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>());
            
            for (Player p : session.getAlivePlayers()) {
                if (!roundDesc.containsKey(p.getUserId())) {
                    roundDesc.put(p.getUserId(), "Người này không nhắn gì cả");
                }
            }
            broadcastDescriptions(session);
            moveToDiscussing(session);
        }
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

    public void onRoleCheckPhaseEnd(String matchId) {
        GameSession session = getSession(matchId);
        if (session.getState() == GameState.ROLE_CHECK) {
            // Người chưa đoán → ghi nhận là sai (không nhận lợi ích)
            for (Player player : session.getAlivePlayers()) {
                if (player.isAi()) continue;
                if (!session.hasSubmittedRoleGuess(player.getUserId())) {
                    session.getRoleCheckResults().put(player.getUserId(), false);
                }
            }
            // Hủy timer nếu có và chuyển phase
            timerService.cancelTimer(matchId);
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

    private void autoRoleCheckForAi(GameSession session) {
        if (session.getState() != GameState.ROLE_CHECK) return;
        for (Player p : session.getAlivePlayers()) {
            if (p.isAi() && !session.hasSubmittedRoleGuess(p.getUserId())) {
                try {
                    submitRoleGuess(session.getMatchId(), p.getUserId(), "civilian");
                } catch (Exception ignored) { }
            }
        }
    }

    private void autoDescribeForAi(GameSession session, String context) {
        if (session.getState() != GameState.DESCRIBING) return;
        Player ai = getAiPlayer(session);
        
        Map<String, String> roundDesc = session.getDescriptions()
                .computeIfAbsent(session.getCurrentRound(), k -> new HashMap<>());
        
        if (!roundDesc.containsKey(ai.getUserId())) {
            try {
                // Sử dụng context (mô tả của human) để AI mô tả theo
                String content = aiService.getAiDescription(session.getCivilianKeyword(), session.getCurrentRound());
                roundDesc.put(ai.getUserId(), content);
                broadcastDescriptions(session);
            } catch (Exception ignored) { }
        }
    }

    private void autoDiscussForAi(GameSession session, String context) {
        if (session.getState() != GameState.DISCUSSING) return;
        Player ai = getAiPlayer(session);

        try {
            // Giả lập AI suy nghĩ một chút
            String content = aiService.getAiDescription(session.getCivilianKeyword(), session.getCurrentRound());
            
            Map<String, Object> aiMsg = new HashMap<>();
            aiMsg.put("user_id", ai.getUserId());
            aiMsg.put("display_name", getAnonymousName(ai));
            aiMsg.put("color", ai.getColor().toString());
            aiMsg.put("content", content);
            aiMsg.put("sender", getAnonymousName(ai));
            aiMsg.put("sent_at", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend("/topic/match/" + session.getMatchId() + "/chat", (Object) aiMsg);
        } catch (Exception ignored) { }
    }

    private void autoVoteForAi(GameSession session) {
        // AI KHÔNG CÒN KHẢ NĂNG VOTE
        return;
    }

    public Map<String, Object> getGameState(String matchId, String userId) {
        GameSession session = getSession(matchId);
        Player player = session.getPlayer(userId);

        Map<String, Object> state = new HashMap<>();
        state.put("room_code", getRoomCode(session));
        state.put("match_id", matchId);
        state.put("round", session.getCurrentRound());
        state.put("phase", session.getState() != null ? session.getState().toString() : "WAITING");
        
        if (session.getPhaseEndTime() != null) {
            state.put("phase_end_at", session.getPhaseEndTime().toString());
            state.put("remaining_seconds", timerService.getRemainingSeconds(session));
        }

        List<Map<String, Object>> playersList = session.getPlayers().stream().map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("user_id", p.getUserId());
            
            // Nếu game đã bắt đầu (sau giai đoạn WAITING), ẩn danh tính
            if (session.getState() != null && session.getState() != GameState.GAME_OVER) {
                pm.put("display_name", getAnonymousName(p));
            } else {
                pm.put("display_name", p.getDisplayName() != null ? p.getDisplayName() : p.getUsername());
            }
            
            pm.put("color", p.getColor() != null ? p.getColor().toString() : "red");
            pm.put("is_alive", p.isAlive());
            pm.put("role", p.getRole() != null ? p.getRole().toString() : "civilian");
            return pm;
        }).toList();
        
        state.put("players", playersList);

        if (player != null) {
            state.put("your_role", player.getRole() != null ? player.getRole().toString() : "civilian");
            
            // Cập nhật role thực sự cho người bị tha hóa
            if (player.isInfected()) {
                state.put("your_role", "infected");
            }

            state.put("your_color", player.getColor() != null ? player.getColor().toString() : "red");
            
            // Trả về từ khóa phù hợp (Infected dùng từ khóa của Spy)
            state.put("your_keyword", (player.getRole() == PlayerRole.spy || player.isInfected())
                    ? session.getSpyKeyword()
                    : session.getCivilianKeyword());
            
            // Trả về kỹ năng đã chọn nếu là Spy
            if (player.getRole() == PlayerRole.spy) {
                state.put("selected_ability", session.getAbilityType());
            }

            // THÊM: Gửi kèm kết quả đoán vai nếu đang ở phase kết quả
            if (session.getState() == GameState.ROLE_CHECK_RESULT) {
                boolean correct = session.getRoleCheckResults().getOrDefault(userId, false);
                state.put("role_check_correct", correct);
                
                if (player.getRole() == PlayerRole.spy && correct) {
                    state.put("can_use_ability", true);
                    state.put("abilities", getAvailableAbilitiesForSpy(session));
                    state.put("alive_humans", getAliveCivilianList(session));
                }
            }
        }

        return state;
    }

    private List<Map<String, String>> getAvailableAbilitiesForSpy(GameSession session) {
        boolean aiAlive = isAiAlive(session);
        List<Map<String, String>> abilities = new ArrayList<>();
        if (aiAlive) {
            abilities.add(Map.of(
                "type", "fake_message",
                "name", "Thao túng AI",
                "description", "Bạn có thể chat thay cho AI (hoặc để AI tự chat) mỗi vòng 1 lần."
            ));
        }
        abilities.add(Map.of(
            "type", "infection",
            "name", "Tha Hóa",
            "description", "Chọn 1 người chơi để biến thành đồng minh (nhận từ khóa của bạn)."
        ));
        return abilities;
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
            if (player.isAi()) continue;
            
            Map<String, Object> roleMsg = new HashMap<>();
            roleMsg.put("match_id", session.getMatchId());
            roleMsg.put("round", session.getCurrentRound());
            
            // CHỈ THÔNG BÁO TỪ KHÓA, KHÔNG CUNG CẤP DANH TÍNH (ROLE) KHI BẮT ĐẦU
            roleMsg.put("role", "unknown"); 
            
            roleMsg.put("color", player.getColor().toString());
            roleMsg.put("your_keyword", player.getRole() == PlayerRole.spy
                    ? session.getSpyKeyword()
                    : session.getCivilianKeyword());
            
            // Principal name trong Spring Security là username hoặc email dùng lúc login
            messagingTemplate.convertAndSendToUser(player.getUsername(), "/queue/role", roleMsg);
        }
    }

    private void broadcastPhase(GameSession session) {
        Map<String, Object> phaseMsg = new HashMap<>();
        phaseMsg.put("room_code", getRoomCode(session));
        phaseMsg.put("match_id", session.getMatchId());
        phaseMsg.put("phase", session.getState() != null ? session.getState().toString() : "WAITING");
        phaseMsg.put("round", session.getCurrentRound());
        if (session.getPhaseEndTime() != null) {
            phaseMsg.put("phase_end_at", session.getPhaseEndTime().toString());
            phaseMsg.put("remaining_seconds", timerService.getRemainingSeconds(session));
        }
        
        // Debug info for FE console
        phaseMsg.put("debug_timestamp", LocalDateTime.now().toString());
        phaseMsg.put("debug_state", session.getState());
        
        phaseMsg.put("players", session.getPlayers().stream().map(p -> {
            Map<String, Object> pm = new HashMap<>();
            pm.put("user_id", p.getUserId());
            
            // Ẩn danh tính tương tự getGameState
            if (session.getState() != null && session.getState() != GameState.GAME_OVER) {
                pm.put("display_name", getAnonymousName(p));
            } else {
                pm.put("display_name", p.getDisplayName() != null ? p.getDisplayName() : p.getUsername());
            }
            
            pm.put("color", p.getColor() != null ? p.getColor().toString() : "red");
            pm.put("is_alive", p.isAlive());
            return pm;
        }).toList());
        
        messagingTemplate.convertAndSend("/topic/match/" + session.getMatchId(), (Object) phaseMsg);
    }

    private void broadcastDescriptions(GameSession session) {
        Map<String, Object> data = new HashMap<>();
        data.put("match_id", session.getMatchId());
        data.put("round", session.getCurrentRound());
        data.put("all_submitted", allPlayersDescribed(session));
        
        // Tạo danh sách chi tiết để FE dễ render gần Avatar
        List<Map<String, Object>> detailList = new ArrayList<>();
        Map<String, String> rawDescs = session.getCurrentRoundDescriptions();
        
        for (Player p : session.getPlayers()) {
            if (rawDescs.containsKey(p.getUserId())) {
                Map<String, Object> item = new HashMap<>();
                item.put("user_id", p.getUserId());
                item.put("content", rawDescs.get(p.getUserId()));
                item.put("color", p.getColor() != null ? p.getColor().toString() : "red");
                
                // Hiển thị tên ẩn danh tương tự broadcastPhase
                if (session.getState() != GameState.GAME_OVER) {
                    item.put("display_name", getAnonymousName(p));
                } else {
                    item.put("display_name", p.getDisplayName() != null ? p.getDisplayName() : p.getUsername());
                }
                
                detailList.add(item);
            }
        }
        
        data.put("descriptions", detailList); // Gửi list chi tiết thay vì Map đơn giản
        
        messagingTemplate.convertAndSend(
                "/topic/match/" + session.getMatchId() + "/descriptions",
                (Object) data);
    }

    private void broadcastVoteCounts(GameSession session) {
        // KHÔNG HIỂN THỊ SỐ LƯỢNG PHIẾU, CHỈ GỬI TRẠNG THÁI ĐÃ VOTE HAY CHƯA
        Map<String, Object> voteStatus = new HashMap<>();
        Map<String, String> currentVotes = session.getVotes().getOrDefault(session.getCurrentRound(), new HashMap<>());
        
        for (Player p : session.getAlivePlayers()) {
            voteStatus.put(p.getUserId(), currentVotes.containsKey(p.getUserId()) ? 1 : 0);
        }

        messagingTemplate.convertAndSend(
                "/topic/match/" + session.getMatchId() + "/votes",
                (Object) voteStatus);
    }

    private void broadcastRoundResult(GameSession session) {
        Player eliminated = session.getPlayer(session.getEliminatedUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("match_id", session.getMatchId());
        result.put("round", session.getCurrentRound());
        result.put("type", "ROUND_RESULT");
        
        if (eliminated != null) {
            result.put("eliminated_user_id", eliminated.getUserId());
            // Ẩn vai trò thực sự trong thông báo, chỉ nói ai bị loại
            result.put("eliminated_display_name", "Người chơi " + eliminated.getColor().toString());
            result.put("message", "Người chơi " + eliminated.getColor().toString() + " đã bị loại!");
        } else {
            result.put("message", "Không có ai bị loại vòng này (Hòa phiếu)");
        }
        messagingTemplate.convertAndSend(
                "/topic/match/" + session.getMatchId() + "/round-result", (Object) result);
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
                "/topic/match/" + session.getMatchId() + "/game-over", (Object) gameOver);
    }

    // =========================================================
    // SECTION 10: PRIVATE HELPERS
    // =========================================================

    private String getAnonymousName(Player p) {
        if (p.getColor() == null) return "Ẩn danh";
        String color = p.getColor().toString().toLowerCase();
        return switch (color) {
            case "red" -> "Mèo Béo";
            case "blue" -> "Cún Con";
            case "green" -> "Gấu Trúc";
            case "yellow" -> "Vịt Vàng";
            case "purple" -> "Cáo Nhỏ";
            case "orange" -> "Hổ Con";
            case "pink" -> "Thỏ Ngọc";
            case "cyan" -> "Chim Cánh Cụt";
            case "brown" -> "Sóc Chuột";
            case "gray" -> "Voi Con";
            case "white" -> "Ngựa Vằn";
            case "black" -> "Cá Heo";
            default -> "Người chơi " + p.getColor().toString();
        };
    }

    private String getRoomCode(GameSession session) {
        if (session.getRoomCode() == null && session.getRoomId() != null) {
            roomRepository.findById(session.getRoomId()).ifPresent(r -> {
                session.setRoomCode(r.getRoomCode());
            });
        }
        return session.getRoomCode();
    }

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
        
        // Cập nhật Match metadata
        matchRepository.findById(matchId).ifPresent(m -> {
            m.setWinnerRole(winner);
            m.setInfectedUserId(session.getInfectedUserId());
            m.setTotalRounds(session.getCurrentRound());
            m.setStatus(MatchStatus.finished);
            m.setEndedAt(LocalDateTime.now());
            matchRepository.save(m);
        });

        if (winner == WinnerRole.spy) {
            // SPY THẮNG: Thưởng Spy 350, Infected 120, Last Survivor 70
            String spyId = session.getSpyUserId();
            Player spyPlayer = session.getPlayer(spyId);
            if (spyPlayer != null && !spyPlayer.isAi()) {
                economyService.addReward(spyId, 350, Transaction.TransactionType.WIN_REWARD, "Spy Thắng Ván: " + matchId, true);
                updateUserStats(spyId, true, PlayerRole.spy);
                updateMatchPlayerWin(matchId, spyId);
            }

            if (session.getInfectedUserId() != null) {
                Player infected = session.getPlayer(session.getInfectedUserId());
                if (infected != null && infected.isAlive() && !infected.isAi()) {
                    economyService.addReward(session.getInfectedUserId(), 120, Transaction.TransactionType.WIN_REWARD, "Infected Thắng Ván: " + matchId, true);
                    updateUserStats(session.getInfectedUserId(), true, PlayerRole.civilian); // Infected là Civilian thắng cùng Spy
                    updateMatchPlayerWin(matchId, session.getInfectedUserId());
                }
            }

            // Dân thường sống sót cuối cùng (không tính spy/infected)
            session.getAlivePlayers().stream()
                .filter(p -> !p.isAi() && !p.getUserId().equals(session.getSpyUserId()) && !p.getUserId().equals(session.getInfectedUserId()))
                .findFirst()
                .ifPresent(p -> {
                    economyService.addReward(p.getUserId(), 70, Transaction.TransactionType.WIN_REWARD, "Dân thường sống sót cuối cùng ván: " + matchId, true);
                    updateUserStats(p.getUserId(), false, PlayerRole.civilian);
                });
            
            // Những người khác thua (chỉ tính human)
            session.getPlayers().stream()
                .filter(p -> !p.isAi() && !p.getUserId().equals(spyId) && (session.getInfectedUserId() == null || !p.getUserId().equals(session.getInfectedUserId())))
                .forEach(p -> updateUserStats(p.getUserId(), false, p.getRole()));

        } else if (winner == WinnerRole.civilians) {
            // DÂN THƯỜNG THẮNG: Mỗi dân thường (sống/chết) nhận 135
            session.getPlayers().stream()
                .filter(p -> !p.isAi() && p.getRole() == PlayerRole.civilian && !p.getUserId().equals(session.getInfectedUserId()))
                .forEach(p -> {
                    economyService.addReward(p.getUserId(), 135, Transaction.TransactionType.WIN_REWARD, "Dân thường Thắng Ván: " + matchId, true);
                    updateUserStats(p.getUserId(), true, PlayerRole.civilian);
                    updateMatchPlayerWin(matchId, p.getUserId());
                });
            
            // Spy và Infected thua (chỉ tính human)
            Player spyPlayer = session.getPlayer(session.getSpyUserId());
            if (spyPlayer != null && !spyPlayer.isAi()) {
                updateUserStats(session.getSpyUserId(), false, PlayerRole.spy);
            }
            if (session.getInfectedUserId() != null) {
                Player infected = session.getPlayer(session.getInfectedUserId());
                if (infected != null && !infected.isAi()) {
                    updateUserStats(session.getInfectedUserId(), false, PlayerRole.civilian);
                }
            }
        }
    }

    private void updateUserStats(String userId, boolean isWin, PlayerRole role) {
        userStatsRepository.findByUserId(userId).ifPresent(stats -> {
            stats.setTotalGames(stats.getTotalGames() + 1);
            if (isWin) {
                if (role == PlayerRole.spy) stats.setWinsSpy(stats.getWinsSpy() + 1);
                else stats.setWinsCivilian(stats.getWinsCivilian() + 1);
            }
            if (role == PlayerRole.spy) stats.setTimesAsSpy(stats.getTimesAsSpy() + 1);
            
            // TODO: Cập nhật correctVotes dựa trên VoteLog (Sprint sau)
            
            stats.setUpdatedAt(LocalDateTime.now());
            userStatsRepository.save(stats);
        });
    }

    private void updateMatchPlayerWin(String matchId, String userId) {
        matchPlayerRepository.findByMatchId(matchId).stream()
                .filter(mp -> mp.getUserId().equals(userId))
                .findFirst()
                .ifPresent(mp -> {
                    mp.setDidWin(true);
                    matchPlayerRepository.save(mp);
                });
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
                    m.put("display_name", getAnonymousName(p));
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

    public void adjustRewards(String matchId, String hostId, Integer civilian, Integer spy, Integer infected) {
        GameSession session = getSession(matchId);
        Room room = roomRepository.findById(session.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        if (!room.getHostId().equals(hostId)) {
            throw new RuntimeException("Only host can adjust rewards");
        }

        if (civilian != null) session.setRewardCivilianGuess(civilian);
        if (spy != null) session.setRewardSpyGuess(spy);
        if (infected != null) session.setRewardInfectedGuess(infected);
    }

    public void adminSetSpy(String roomId, String hostId, String spyUserId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        if (!room.getHostId().equals(hostId)) {
            throw new RuntimeException("Only host can set the spy");
        }
        
        if (room.getStatus() != RoomStatus.waiting) {
            throw new RuntimeException("Can only set spy while in waiting room");
        }
        
        // Kiểm tra xem user có trong phòng không
        List<RoomPlayer> players = roomPlayerRepository.findByRoomId(roomId);
        boolean userInRoom = players.stream().anyMatch(rp -> rp.getUserId().equals(spyUserId));
        if (!userInRoom) {
            throw new RuntimeException("Selected user is not in the room");
        }
        
        room.setAdminSelectedSpyId(spyUserId);
        roomRepository.save(room);
    }

    @Profile("dev")
    public void setGameSpyDebug(String matchId, String userId) {
        GameSession session = getSession(matchId);
        
        // Reset role cho tất cả người chơi cũ
        for (Player p : session.getPlayers()) {
            p.setRole(PlayerRole.civilian);
        }
        
        // Gán spy cho user mong muốn
        Player spy = session.getPlayer(userId);
        if (spy == null) throw new RuntimeException("User not found in session");
        
        spy.setRole(PlayerRole.spy);
        session.setSpyUserId(userId);
        
        // Cập nhật Match metadata
        matchRepository.findById(matchId).ifPresent(m -> {
            m.setSpyUserId(userId);
            matchRepository.save(m);
        });
        
        // Gửi lại role (bí mật)
        broadcastRoles(session);
    }
}
