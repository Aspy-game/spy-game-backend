package com.keywordspy.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.keywordspy.game.model.Role;
import lombok.Data;

@Data
public class RegisterRequest {
    private Role role;
    private String username;
    private String email;
    private String password;
    @JsonProperty("display_name")
    private String displayName;
}
