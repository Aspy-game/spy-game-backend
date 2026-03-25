package com.keywordspy.game.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String username;
    private String email;
    private String token;
    private String newPassword;
}
