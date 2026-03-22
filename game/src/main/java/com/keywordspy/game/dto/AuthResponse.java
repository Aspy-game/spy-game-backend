package com.keywordspy.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.keywordspy.game.model.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    @JsonProperty("user_id")
    private String userId;
    
    private String username;
    
    @JsonProperty("display_name")
    private String displayName;
    
    @JsonProperty("avatar_url")
    private String avatarUrl;
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("expires_in")
    private Long expiresIn;

    private Role role;

    private Integer balance;
}
