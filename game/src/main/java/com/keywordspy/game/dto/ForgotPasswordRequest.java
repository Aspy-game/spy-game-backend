package com.keywordspy.game.dto;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String username;
    private String email;
}
