package com.zero1blog.backend.service;

import com.zero1blog.backend.dto.AdminStatsResponse;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.dto.UserAdminResponse;
import com.zero1blog.backend.model.Report;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.model.UserProfile;
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
    private final UserProfileRepository userProfileRepository;

    public AdminService(UserRepository userRepository, PostRepository postRepository,
                        CommentRepository commentRepository, ReportRepository reportRepository,
                        ReportService reportService, UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.userProfileRepository = userProfileRepository;
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
    public void updateUserRole(String username, String roleName, String callerPublicId) {
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User caller = userRepository.findByPublicId(callerPublicId)
                .orElseThrow(() -> new RuntimeException("Caller not found"));

        User.Role targetNewRole = User.Role.valueOf(roleName.toUpperCase());

        if (targetUser.getRole() == User.Role.ADMIN || targetUser.getRole() == User.Role.SUPER_ADMIN ||
            targetNewRole == User.Role.ADMIN || targetNewRole == User.Role.SUPER_ADMIN) {
            if (caller.getRole() != User.Role.SUPER_ADMIN) {
                throw new RuntimeException("Only SUPER_ADMIN can promote or demote administrators.");
            }
        }

        log.info("Admin {} updating user role for {}: {}", caller.getUsername(), username, roleName);
        targetUser.setRole(targetNewRole);
        userRepository.save(targetUser);
    }

    @Transactional
    public void toggleBan(String username, String adminPublicId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User caller = userRepository.findByPublicId(adminPublicId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        if (user.getPublicId().equals(adminPublicId)) {
            throw new RuntimeException("You cannot ban yourself.");
        }

        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.SUPER_ADMIN) {
            if (caller.getRole() != User.Role.SUPER_ADMIN) {
                throw new RuntimeException("Only SUPER_ADMIN can ban or unban administrators.");
            }
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
        User caller = userRepository.findByPublicId(adminPublicId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        if (user.getPublicId().equals(adminPublicId)) {
            throw new RuntimeException("You cannot ban yourself.");
        }

        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.SUPER_ADMIN) {
            if (caller.getRole() != User.Role.SUPER_ADMIN) {
                throw new RuntimeException("Only SUPER_ADMIN can ban administrators.");
            }
        }

        log.warn("Admin BANNING user: {}", username);
        user.setBanned(true);
        userRepository.save(user);
    }

    @Transactional
    public void deletePost(Long postId) {
        log.warn("Admin DELETING post ID: {}", postId);
        postRepository.findById(postId).ifPresent(post -> {
            if (post.getMediaUrl() != null && post.getMediaUrl().startsWith("/api/media/files/")) {
                try {
                    String fileName = post.getMediaUrl().substring("/api/media/files/".length());
                    java.nio.file.Path filePath = java.nio.file.Paths.get("uploads").resolve(fileName);
                    java.nio.file.Files.deleteIfExists(filePath);
                } catch (Exception e) {
                    log.error("Failed to delete media file for post " + postId, e);
                }
            }
            postRepository.delete(post);
        });
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
                .avatarUrl(user.getProfile() != null ? user.getProfile().getAvatarUrl() : null)
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Transactional
    public void updateDisplayName(String username, String displayName, String callerUsername) {
        log.info("Admin {} updating display name for user {}: {}", callerUsername, username, displayName);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserProfile profile = userProfileRepository.findByUser(user)
                .orElse(UserProfile.builder().user(user).build());
        profile.setDisplayName(displayName);
        userProfileRepository.save(profile);
    }
}
