package com.zero1blog.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAdminResponse {
    private Long id;
    private String publicId;
    private String username;
    private String email;
    private String role;
    
    @JsonProperty("isBanned")
    private boolean isBanned;

    private String banReason;
    private LocalDateTime bannedUntil;
    private String avatarUrl;
    private String displayName;
    private LocalDateTime createdAt;
}
