package com.zero1blog.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CommentRequest {

    @NotBlank
    private String content;

    private String mediaUrl;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
}
