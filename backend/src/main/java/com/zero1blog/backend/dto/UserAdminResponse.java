package com.zero1blog.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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
    private boolean isBanned;
    private LocalDateTime createdAt;
}
