package com.zero1blog.backend.dto;

import java.time.LocalDateTime;

public class MessageResponse {
    private Long id;
    private String senderPublicId;
    private String senderUsername;
    private String senderAvatarUrl;
    private String recipientPublicId;
    private String recipientUsername;
    private String recipientAvatarUrl;
    private String content;
    private String mediaUrl;
    private boolean isRead;
    private LocalDateTime createdAt;

    public MessageResponse(Long id, String senderPublicId, String senderUsername, String senderAvatarUrl,
                           String recipientPublicId, String recipientUsername, String recipientAvatarUrl,
                           String content, String mediaUrl, boolean isRead, LocalDateTime createdAt) {
        this.id = id;
        this.senderPublicId = senderPublicId;
        this.senderUsername = senderUsername;
        this.senderAvatarUrl = senderAvatarUrl;
        this.recipientPublicId = recipientPublicId;
        this.recipientUsername = recipientUsername;
        this.recipientAvatarUrl = recipientAvatarUrl;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    // Getters
    public Long getId() { return id; }
    public String getSenderPublicId() { return senderPublicId; }
    public String getSenderUsername() { return senderUsername; }
    public String getSenderAvatarUrl() { return senderAvatarUrl; }
    public String getRecipientPublicId() { return recipientPublicId; }
    public String getRecipientUsername() { return recipientUsername; }
    public String getRecipientAvatarUrl() { return recipientAvatarUrl; }
    public String getContent() { return content; }
    public String getMediaUrl() { return mediaUrl; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
