package com.zero1blog.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class MessageRequest {

    @NotBlank
    private String recipientPublicId;

    @NotBlank
    private String content;

    private String mediaUrl;

    public String getRecipientPublicId() { return recipientPublicId; }
    public void setRecipientPublicId(String recipientPublicId) { this.recipientPublicId = recipientPublicId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
}
