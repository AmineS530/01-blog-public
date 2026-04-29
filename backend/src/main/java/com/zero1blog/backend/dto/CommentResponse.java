package com.zero1blog.backend.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CommentResponse {
    private Long id;
    private String content;
    private String mediaUrl;
    private String authorUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long likeCount;
    
    @JsonProperty("isLikedByCurrentUser")
    private boolean isLikedByCurrentUser;

    public CommentResponse(Long id, String content, String mediaUrl, String authorUsername, LocalDateTime createdAt, LocalDateTime updatedAt, long likeCount, boolean isLikedByCurrentUser) {
        this.id = id;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.authorUsername = authorUsername;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.likeCount = likeCount;
        this.isLikedByCurrentUser = isLikedByCurrentUser;
    }

    public Long getId() { return id; }
    public String getContent() { return content; }
    public String getMediaUrl() { return mediaUrl; }
    public String getAuthorUsername() { return authorUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getLikeCount() { return likeCount; }
    public boolean isLikedByCurrentUser() { return isLikedByCurrentUser; }
}
