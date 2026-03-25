package com.keywordspy.game.dto;

import lombok.Data;

@Data
public class VerifyTokenRequest {
    private String username;
    private String email;
    private String token;
}
