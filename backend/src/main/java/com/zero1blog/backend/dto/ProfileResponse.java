package com.zero1blog.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponse {
    private String publicId;
    private String username;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private long followerCount;
    private long followingCount;

    // True when the current user is following this profile
    // (computed only when caller is authenticated).
    private boolean isFollowing;

    // True when the current user has blocked this profile.
    // When true, hide this profile from the caller's feed, comments, and lists.
    private boolean isBlocked;

    // True when this profile has blocked the current user.
    // Should be treated identically to "this profile does not exist" for the caller
    // (return Not Found from /api/profiles/{username} when this flag is true).
    private boolean isBlockingMe;
}
