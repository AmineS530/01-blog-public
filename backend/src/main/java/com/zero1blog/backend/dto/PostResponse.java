package com.zero1blog.backend.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PostResponse {
    private Long id;
    private String title;
    private String content;
    private String mediaUrl;
    private String authorUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long commentCount;
    private long likeCount;
    
    @JsonProperty("isLikedByCurrentUser")
    private boolean isLikedByCurrentUser;

    public PostResponse(Long id, String title, String content, String mediaUrl,
                        String authorUsername, LocalDateTime createdAt, LocalDateTime updatedAt,
                        long commentCount, long likeCount, boolean isLikedByCurrentUser) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.authorUsername = authorUsername;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.commentCount = commentCount;
        this.likeCount = likeCount;
        this.isLikedByCurrentUser = isLikedByCurrentUser;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getMediaUrl() { return mediaUrl; }
    public String getAuthorUsername() { return authorUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getCommentCount() { return commentCount; }
    public long getLikeCount() { return likeCount; }
    public boolean isLikedByCurrentUser() { return isLikedByCurrentUser; }
}