package com.zero1blog.backend.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PostResponse {
    private String publicId;
    private String title;
    private String content;
    private String mediaUrl;
    private String authorUsername;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String authorPublicId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long commentCount;
    private long likeCount;

    @JsonProperty("isLikedByCurrentUser")
    private boolean isLikedByCurrentUser;

    public PostResponse(String publicId, String title, String content, String mediaUrl,
            String authorUsername, String authorDisplayName, String authorAvatarUrl, String authorPublicId, LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long commentCount, long likeCount, boolean isLikedByCurrentUser) {
        this.publicId = publicId;
        this.title = title;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.authorUsername = authorUsername;
        this.authorDisplayName = authorDisplayName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.authorPublicId = authorPublicId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.commentCount = commentCount;
        this.likeCount = likeCount;
        this.isLikedByCurrentUser = isLikedByCurrentUser;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public String getAuthorDisplayName() {
        return authorDisplayName;
    }

    public String getAuthorAvatarUrl() {
        return authorAvatarUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public boolean isLikedByCurrentUser() {
        return isLikedByCurrentUser;
    }

    public String getAuthorPublicId() {
        return authorPublicId;
    }
}