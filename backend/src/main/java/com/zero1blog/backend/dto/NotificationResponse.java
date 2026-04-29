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
public class NotificationResponse {
    private Long id;
    private String type;
    private String message;
    private boolean isRead;
    private String actorUsername;
    private Long postId;
    private LocalDateTime createdAt;
}
