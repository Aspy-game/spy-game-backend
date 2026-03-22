package com.keywordspy.game.manager;

import com.keywordspy.game.model.GameSession;
import com.keywordspy.game.model.GameSession.GameState;
import org.springframework.stereotype.Component;

@Component
public class GameStateMachine {


    public void transition(GameSession session, GameState newState) {
        GameState currentState = session.getState();

        if (!isValidTransition(currentState, newState)) {
            throw new RuntimeException(
                "Invalid state transition: " + currentState + " -> " + newState);
        }

        session.setState(newState);
        session.setUpdatedAt(java.time.LocalDateTime.now());
    }


    private boolean isValidTransition(GameState from, GameState to) {
        switch (from) {
            case WAITING:
                return to == GameState.ROLE_ASSIGN;
            case ROLE_ASSIGN:
                return to == GameState.DESCRIBING;
            case DESCRIBING:
                return to == GameState.DISCUSSING;
            case DISCUSSING:
                return to == GameState.VOTING;
            case VOTING:
                return to == GameState.VOTE_TIE || to == GameState.ROUND_RESULT;
            case VOTE_TIE:
                return to == GameState.ROLE_CHECK || to == GameState.DESCRIBING || to == GameState.ROUND_RESULT;
            case ROUND_RESULT:
                return to == GameState.ROLE_CHECK || to == GameState.DESCRIBING || to == GameState.GAME_OVER;
            case ROLE_CHECK:
                return to == GameState.ROLE_CHECK_RESULT;
            case ROLE_CHECK_RESULT:
                return to == GameState.DESCRIBING;
            case GAME_OVER:
                return false;
            default:
                return false;
        }
    }

    public boolean isPhase(GameSession session, GameState state) {
        return session.getState() == state;
    }

