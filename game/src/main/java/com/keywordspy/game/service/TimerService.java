package com.keywordspy.game.service;

import com.keywordspy.game.model.GameSession;
import com.keywordspy.game.model.GameSession.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@EnableAsync
public class TimerService {


    // =========================================================
    // THỜI GIAN MỖI PHASE (giây)
    // Đang để 300s (5 phút) để tiện test — chỉnh lại khi release
    // =========================================================
    public static final int DESCRIBE_DURATION          = 300;
    public static final int DISCUSS_DURATION           = 300;
    public static final int VOTE_DURATION              = 300;
    public static final int VOTE_TIE_DURATION          = 10;
    public static final int ROUND_RESULT_DURATION      = 5;
    public static final int ROLE_CHECK_DURATION        = 300;
    public static final int ROLE_CHECK_RESULT_DURATION = 300;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Map<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    @Autowired
    @Lazy
    private GameService gameService;

    @Autowired
    private SettingsService settingsService;


    // =========================================================
    // GET DURATIONS
    // =========================================================

    public int getDescribeDuration() {
        return settingsService.find().map(s -> s.getDescribeDuration() != null ? s.getDescribeDuration() : DESCRIBE_DURATION).orElse(DESCRIBE_DURATION);
    }

    public int getDiscussDuration() {
        return settingsService.find().map(s -> s.getDiscussDuration() != null ? s.getDiscussDuration() : DISCUSS_DURATION).orElse(DISCUSS_DURATION);
    }

    public int getVoteDuration() {
        return settingsService.find().map(s -> s.getVoteDuration() != null ? s.getVoteDuration() : VOTE_DURATION).orElse(VOTE_DURATION);
    }

    public int getRoleCheckDuration() {
        return settingsService.find().map(s -> s.getRoleCheckDuration() != null ? s.getRoleCheckDuration() : ROLE_CHECK_DURATION).orElse(ROLE_CHECK_DURATION);
    }

    public int getRoleCheckResultDuration() {
        return settingsService.find().map(s -> s.getRoleCheckResultDuration() != null ? s.getRoleCheckResultDuration() : ROLE_CHECK_RESULT_DURATION).orElse(ROLE_CHECK_RESULT_DURATION);
    }

    // =========================================================
    // START TIMERS
    // =========================================================

    public void startDescribeTimer(String matchId) {
        startTimer(matchId, getDescribeDuration(), () ->
                gameService.onDescribePhaseEnd(matchId));
    }

    public void startDiscussTimer(String matchId) {
        startTimer(matchId, getDiscussDuration(), () ->
                gameService.onDiscussPhaseEnd(matchId));
    }

    public void startVoteTimer(String matchId) {
        startTimer(matchId, getVoteDuration(), () ->
                gameService.onVotePhaseEnd(matchId));
    }

    public void startVoteTieTimer(String matchId) {
        startTimer(matchId, VOTE_TIE_DURATION, () ->
                gameService.onVoteTieEnd(matchId));
    }

    public void startRoundResultTimer(String matchId) {
        startTimer(matchId, ROUND_RESULT_DURATION, () ->
                gameService.onRoundResultEnd(matchId));
    }

    // Phase ROLE_CHECK: 20s tất cả đoán vai trò
    public void startRoleCheckTimer(String matchId) {
        startTimer(matchId, getRoleCheckDuration(), () ->
                gameService.onRoleCheckPhaseEnd(matchId));
    }

    // Phase ROLE_CHECK_RESULT: 20s hiện kết quả cá nhân + Spy chọn Tha Hóa
    public void startRoleCheckResultTimer(String matchId) {
        startTimer(matchId, getRoleCheckResultDuration(), () ->
                gameService.onRoleCheckResultPhaseEnd(matchId));
    }

    // =========================================================
    // CANCEL & UTILS
    // =========================================================

    public void cancelTimer(String matchId) {
        ScheduledFuture<?> future = activeTimers.remove(matchId);
        if (future != null) {
            future.cancel(false);
        }
    }


    public int getRemainingSeconds(GameSession session) {
        if (session.getPhaseEndTime() == null) return 0;
        long remaining = java.time.Duration.between(
                LocalDateTime.now(), session.getPhaseEndTime()).getSeconds();
        return (int) Math.max(0, remaining);
    }

    public void startTimer(String matchId, int durationSeconds, Runnable onComplete) {
        cancelTimer(matchId); // hủy timer cũ nếu có


        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                activeTimers.remove(matchId);
                onComplete.run();
            } catch (Exception e) {
                System.err.println("[TIMER-ERROR] Error executing callback for match: " + matchId);
                e.printStackTrace();
            }
        }, durationSeconds, TimeUnit.SECONDS);

        activeTimers.put(matchId, future);
    }
}
