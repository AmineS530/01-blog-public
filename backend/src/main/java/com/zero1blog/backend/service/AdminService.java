package com.zero1blog.backend.service;

import com.zero1blog.backend.dto.AdminStatsResponse;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.dto.UserAdminResponse;
import com.zero1blog.backend.model.Report;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final ReportService reportService;

    public AdminService(UserRepository userRepository, PostRepository postRepository,
                        CommentRepository commentRepository, ReportRepository reportRepository,
                        ReportService reportService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        LocalDateTime todayStart = LocalDateTime.now().minusDays(1);
        return AdminStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalPosts(postRepository.count())
                .totalComments(commentRepository.count())
                .totalReports(reportRepository.count())
                .pendingReports(reportRepository.countByStatus("pending"))
                .bannedUsers(userRepository.countByIsBanned(true))
                .newUsersToday(userRepository.countByCreatedAtAfter(todayStart))
                .newPostsToday(postRepository.countByCreatedAtAfter(todayStart))
                .newCommentsToday(commentRepository.countByCreatedAtAfter(todayStart))
                .build();
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getReports(String status, int page, int limit) {
        Page<Report> reports = reportRepository.findByStatus(status, PageRequest.of(page, limit));
        return reports.getContent().stream()
                .map(reportService::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<UserAdminResponse> getUsers(String query, int page, int limit) {
        Page<User> users;
        if (query != null && !query.isEmpty()) {
            users = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query, PageRequest.of(page, limit));
        } else {
            users = userRepository.findAll(PageRequest.of(page, limit));
        }
        return users.map(this::toUserAdminResponse);
    }

    @Transactional
    public void updateUserRole(String username, String roleName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        log.info("Admin updating user role for {}: {}", username, roleName);
        user.setRole(User.Role.valueOf(roleName.toUpperCase()));
        userRepository.save(user);
    }

    @Transactional
    public void toggleBan(String username, String adminPublicId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getPublicId().equals(adminPublicId)) {
            throw new RuntimeException("You cannot ban yourself.");
        }

        boolean newStatus = !user.isBanned();
        log.warn("Admin toggling ban for {}: {}", username, newStatus);
        user.setBanned(newStatus);
        userRepository.save(user);
    }

    @Transactional
    public void resolveReport(Long reportId, String action, String note) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        
        log.info("Admin resolving report ID: {}. Action: {}", reportId, action);
        report.setStatus(action.equals("resolve") ? "resolved" : "dismissed");
        report.setNote(note);
        reportRepository.save(report);
    }

    @Transactional
    public void banUser(String username, String adminPublicId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getPublicId().equals(adminPublicId)) {
            throw new RuntimeException("You cannot ban yourself.");
        }

        log.warn("Admin BANNING user: {}", username);
        user.setBanned(true);
        userRepository.save(user);
    }

    @Transactional
    public void deletePost(Long postId) {
        log.warn("Admin DELETING post ID: {}", postId);
        postRepository.deleteById(postId);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        log.warn("Admin DELETING comment ID: {}", commentId);
        commentRepository.deleteById(commentId);
    }

    private UserAdminResponse toUserAdminResponse(User user) {
        return UserAdminResponse.builder()
                .id(user.getId())
                .publicId(user.getPublicId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isBanned(user.isBanned())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
