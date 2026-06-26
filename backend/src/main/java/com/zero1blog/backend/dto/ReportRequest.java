package com.zero1blog.backend.dto;

import lombok.Data;

@Data
public class ReportRequest {
    private String reason;
    private Long targetUserId;
    private String targetPostId;
    private Long targetCommentId;
}
