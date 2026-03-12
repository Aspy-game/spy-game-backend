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

    // Thời gian mỗi phase (giây)
public static final int DESCRIBE_DURATION = 30;
public static final int DISCUSS_DURATION = 30;
public static final int VOTE_DURATION = 300;  // VOTING giữ 300s để kịp vote
public static final int ROLE_CHECK_DURATION = 20; // nếu có

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Map<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    @Autowired
    @Lazy
    private GameService gameService;

    // Bắt đầu timer cho phase mô tả
    public void startDescribeTimer(String matchId) {
        startTimer(matchId, DESCRIBE_DURATION, () -> {
            gameService.onDescribePhaseEnd(matchId);
        });
    }
    

    // Bắt đầu timer cho phase thảo luận
    public void startDiscussTimer(String matchId) {
        startTimer(matchId, DISCUSS_DURATION, () -> {
            gameService.onDiscussPhaseEnd(matchId);
        });
    }

    // Bắt đầu timer cho phase vote
    public void startVoteTimer(String matchId) {
        startTimer(matchId, VOTE_DURATION, () -> {
            gameService.onVotePhaseEnd(matchId);
        });
    }
    public void startRoleCheckTimer(String matchId) {
    startTimer(matchId, ROLE_CHECK_DURATION, () -> {
        gameService.onRoleCheckPhaseEnd(matchId);
    });
}

    // Hủy timer của match
    public void cancelTimer(String matchId) {
        ScheduledFuture<?> future = activeTimers.remove(matchId);
        if (future != null) {
            future.cancel(false);
        }
    }

    // Tính số giây còn lại
    public int getRemainingSeconds(GameSession session) {
        if (session.getPhaseEndTime() == null) return 0;
        long remaining = java.time.Duration.between(
                LocalDateTime.now(), session.getPhaseEndTime()).getSeconds();
        return (int) Math.max(0, remaining);
    }

    private void startTimer(String matchId, int durationSeconds, Runnable onComplete) {
        // Hủy timer cũ nếu có
        cancelTimer(matchId);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            activeTimers.remove(matchId);
            onComplete.run();
        }, durationSeconds, TimeUnit.SECONDS);

        activeTimers.put(matchId, future);
    }
}