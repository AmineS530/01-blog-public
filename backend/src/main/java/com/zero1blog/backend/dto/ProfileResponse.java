package com.zero1blog.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isFollowing")
    private boolean isFollowing;

    // True when the current user has blocked this profile.
    // When true, hide this profile from the caller's feed, comments, and lists.
    @JsonProperty("isBlocked")
    private boolean isBlocked;

    // True when this profile has blocked the current user.
    // Should be treated identically to "this profile does not exist" for the caller
    // (return Not Found from /api/profiles/{username} when this flag is true).
    @JsonProperty("isBlockingMe")
    private boolean isBlockingMe;

    /**
     * Timestamp of the user's most recent username change, ISO-8601. Null if
     * the user has never changed their username. The settings UI uses this
     * together with the configured cooldown (returned by
     * {@code GET /api/auth/username-cooldown}) to render a "you can change
     * again in N days" countdown and to gate the username-change submit.
     *
     * Only meaningful for the caller's own profile; the field is nevertheless
     * populated for any profile shape to keep the DTO uniform.
     */
    private String usernameChangedAt;
}
