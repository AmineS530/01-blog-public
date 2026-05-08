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
public class ReportResponse {
    private Long id;
    private String reason;
    private String status;
    private String reporterUsername;
    private String targetUsername;
    private Long targetPostId;
    private Long targetCommentId;
    private String targetType; // 'USER', 'POST', 'COMMENT'
    private Long targetId; // the ID corresponding to the targetType (could be userId, postId, commentId)
    private LocalDateTime createdAt;
}
