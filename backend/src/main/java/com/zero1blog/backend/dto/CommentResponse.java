package com.zero1blog.backend.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CommentResponse {
    private Long id;
    private String content;
    private String mediaUrl;
    private String authorUsername;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long likeCount;
    
    @JsonProperty("isLikedByCurrentUser")
    private boolean isLikedByCurrentUser;

    public CommentResponse(Long id, String content, String mediaUrl, String authorUsername, String authorDisplayName, String authorAvatarUrl, LocalDateTime createdAt, LocalDateTime updatedAt, long likeCount, boolean isLikedByCurrentUser) {
        this.id = id;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.authorUsername = authorUsername;
        this.authorDisplayName = authorDisplayName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.likeCount = likeCount;
        this.isLikedByCurrentUser = isLikedByCurrentUser;
    }

    public Long getId() { return id; }
    public String getContent() { return content; }
    public String getMediaUrl() { return mediaUrl; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getLikeCount() { return likeCount; }
    public boolean isLikedByCurrentUser() { return isLikedByCurrentUser; }
}
