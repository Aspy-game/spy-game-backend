package com.keywordspy.game.manager;

import com.keywordspy.game.model.GameSession;
import com.keywordspy.game.model.GameSession.GameState;
import org.springframework.stereotype.Component;

@Component
public class GameStateMachine {

    // Chuyển sang state tiếp theo
    public void transition(GameSession session, GameState newState) {
        GameState currentState = session.getState();
        
        if (!isValidTransition(currentState, newState)) {
            throw new RuntimeException("Invalid state transition: " + currentState + " -> " + newState);
        }

        session.setState(newState);
        session.setUpdatedAt(java.time.LocalDateTime.now());
    }

    // Kiểm tra transition hợp lệ không
    private boolean isValidTransition(GameState from, GameState to) {
        return switch (from) {
            case WAITING -> to == GameState.ROLE_ASSIGN;
            case ROLE_ASSIGN -> to == GameState.ROLE_CHECK || to == GameState.DESCRIBING;
            case ROLE_CHECK -> to == GameState.DESCRIBING;
            case DESCRIBING -> to == GameState.DISCUSSING;
            case DISCUSSING -> to == GameState.VOTING;
            case VOTING -> to == GameState.VOTE_TIE || to == GameState.ROUND_RESULT;
            case VOTE_TIE -> to == GameState.ROUND_RESULT;
            case ROUND_RESULT -> to == GameState.INFECTION || to == GameState.ROLE_CHECK || to == GameState.GAME_OVER;
            case INFECTION -> to == GameState.ROLE_CHECK || to == GameState.GAME_OVER;
            case GAME_OVER -> false;
        };
    }

    public boolean isPhase(GameSession session, GameState state) {
        return session.getState() == state;
    }
}