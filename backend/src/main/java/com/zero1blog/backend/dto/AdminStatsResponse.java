package com.zero1blog.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStatsResponse {
    private long totalUsers;
    private long totalPosts;
    private long totalComments;
    private long totalReports;
    private long pendingReports;
    private long bannedUsers;
    
    private long newUsersToday;
    private long newPostsToday;
    private long newCommentsToday;
}
