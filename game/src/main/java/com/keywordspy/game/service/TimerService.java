package com.keywordspy.game.service;

import com.keywordspy.game.model.GameSession;
import com.keywordspy.game.model.GameSession.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    // START TIMERS
    // =========================================================

    public void startDescribeTimer(String matchId) {
        int d = settingsService.find().map(s -> s.getDescribeDuration() != null ? s.getDescribeDuration() : DESCRIBE_DURATION).orElse(DESCRIBE_DURATION);
        startTimer(matchId, d, () ->
                gameService.onDescribePhaseEnd(matchId));
    }

    public void startDiscussTimer(String matchId) {
        int d = settingsService.find().map(s -> s.getDiscussDuration() != null ? s.getDiscussDuration() : DISCUSS_DURATION).orElse(DISCUSS_DURATION);
        startTimer(matchId, d, () ->
                gameService.onDiscussPhaseEnd(matchId));
    }

    public void startVoteTimer(String matchId) {
        int d = settingsService.find().map(s -> s.getVoteDuration() != null ? s.getVoteDuration() : VOTE_DURATION).orElse(VOTE_DURATION);
        startTimer(matchId, d, () ->
                gameService.onVotePhaseEnd(matchId));
    }

    // Phase ROLE_CHECK: 20s tất cả đoán vai trò
    public void startRoleCheckTimer(String matchId) {
        int d = settingsService.find().map(s -> s.getRoleCheckDuration() != null ? s.getRoleCheckDuration() : ROLE_CHECK_DURATION).orElse(ROLE_CHECK_DURATION);
        startTimer(matchId, d, () ->
                gameService.onRoleCheckPhaseEnd(matchId));
    }

    // Phase ROLE_CHECK_RESULT: 20s hiện kết quả cá nhân + Spy chọn Tha Hóa
    public void startRoleCheckResultTimer(String matchId) {
        int d = settingsService.find().map(s -> s.getRoleCheckResultDuration() != null ? s.getRoleCheckResultDuration() : ROLE_CHECK_RESULT_DURATION).orElse(ROLE_CHECK_RESULT_DURATION);
        startTimer(matchId, d, () ->
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

    private void startTimer(String matchId, int durationSeconds, Runnable onComplete) {
        cancelTimer(matchId); // hủy timer cũ nếu có

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            activeTimers.remove(matchId);
            onComplete.run();
        }, durationSeconds, TimeUnit.SECONDS);

        activeTimers.put(matchId, future);
    }
}
