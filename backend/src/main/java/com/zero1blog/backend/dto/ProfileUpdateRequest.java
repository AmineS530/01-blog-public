package com.zero1blog.backend.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String displayName;
    private String bio;
    private String avatarUrl;
}
