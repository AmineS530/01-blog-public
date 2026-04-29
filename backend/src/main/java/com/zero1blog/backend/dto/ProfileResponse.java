package com.zero1blog.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponse {
    private String username;
    private String fullName;
    private String bio;
    private String avatarUrl;
    private long followerCount;
    private long followingCount;
    
    @JsonProperty("isFollowing")
    private boolean isFollowing;
    
    @JsonProperty("isBlocked")
    private boolean isBlocked;
    
    @JsonProperty("isBlockingMe")
    private boolean isBlockingMe;
}
