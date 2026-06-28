package com.zero1blog.backend.service;

import com.zero1blog.backend.dto.AdminStatsResponse;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.dto.UserAdminResponse;
import com.zero1blog.backend.model.Report;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.model.UserProfile;
import com.zero1blog.backend.repository.*;
import com.zero1blog.backend.exception.*;
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

    private static final int MAX_PAGE_SIZE = 100;
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
        int safeLimit = Math.min(limit, MAX_PAGE_SIZE);
        Page<Report> reports = reportRepository.findByStatusWithTargets(status, PageRequest.of(page, safeLimit));
        return reports.getContent().stream()
                .map(reportService::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<UserAdminResponse> getUsers(String query, int page, int limit, String sortBy, String direction) {
        int safeLimit = Math.min(limit, MAX_PAGE_SIZE);
        
        String sortProperty = sortBy;
        if ("displayName".equals(sortBy)) {
            sortProperty = "profile.displayName";
        } else if ("active".equals(sortBy)) {
            sortProperty = "isBanned";
        }
        
        org.springframework.data.domain.Sort sort = "desc".equalsIgnoreCase(direction) ?
                org.springframework.data.domain.Sort.by(sortProperty).descending() :
                org.springframework.data.domain.Sort.by(sortProperty).ascending();
                
        PageRequest pageRequest = PageRequest.of(page, safeLimit, sort);
        Page<User> users;
        if (query != null && !query.isEmpty()) {
            users = userRepository.findByUsernameOrEmailWithProfile(query, query, pageRequest);
        } else {
            users = userRepository.findAllWithProfile(pageRequest);
        }
        return users.map(this::toUserAdminResponse);
    }

    @Transactional
    public void updateUserRole(String username, String roleName, String callerPublicId) {
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User caller = userRepository.findByPublicId(callerPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Caller not found"));

        User.Role targetNewRole = User.Role.valueOf(roleName.toUpperCase());

        if (targetUser.getRole() == User.Role.ADMIN || targetUser.getRole() == User.Role.SUPER_ADMIN ||
            targetNewRole == User.Role.ADMIN || targetNewRole == User.Role.SUPER_ADMIN) {
            if (caller.getRole() != User.Role.SUPER_ADMIN) {
                throw new UnauthorizedActionException("Only SUPER_ADMIN can promote or demote administrators.");
            }
        }

        log.info("Admin {} updating user role for {}: {}", caller.getUsername(), username, roleName);
        targetUser.setRole(targetNewRole);
        userRepository.save(targetUser);
    }

    @Transactional
    public void toggleBan(String username, String adminPublicId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User caller = userRepository.findByPublicId(adminPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        
        if (user.getPublicId().equals(adminPublicId)) {
            throw new BadRequestException("You cannot ban yourself.");
        }

        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.SUPER_ADMIN) {
            if (caller.getRole() != User.Role.SUPER_ADMIN) {
                throw new UnauthorizedActionException("Only SUPER_ADMIN can ban or unban administrators.");
            }
        }

        boolean newStatus = !user.isBanned();
        log.warn("Admin toggling ban for {}: {}", username, newStatus);
        user.setBanned(newStatus);
        if (!newStatus) {
            user.setBanReason(null);
            user.setBannedUntil(null);
        }
        userRepository.save(user);
    }

    @Transactional
    public void resolveReport(Long reportId, String action, String note) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        
        log.info("Admin resolving report ID: {}. Action: {}", reportId, action);
        report.setStatus(action.equals("resolve") ? "resolved" : "dismissed");
        report.setNote(note);
        reportRepository.save(report);
    }

    @Transactional
    public void banUser(String username, String reason, Integer durationMinutes, String adminPublicId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User caller = userRepository.findByPublicId(adminPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        
        if (user.getPublicId().equals(adminPublicId)) {
            throw new BadRequestException("You cannot ban yourself.");
        }

        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.SUPER_ADMIN) {
            if (caller.getRole() != User.Role.SUPER_ADMIN) {
                throw new UnauthorizedActionException("Only SUPER_ADMIN can ban administrators.");
            }
        }

        log.warn("Admin BANNING user: {}. Reason: {}, Duration: {} mins", username, reason, durationMinutes);
        user.setBanned(true);
        user.setBanReason(reason != null && !reason.isBlank() ? reason.trim() : "No reason provided");
        if (durationMinutes != null && durationMinutes > 0) {
            user.setBannedUntil(LocalDateTime.now().plusMinutes(durationMinutes));
        } else {
            user.setBannedUntil(null); // Permanent ban
        }
        userRepository.save(user);
    }

    @Transactional
    public void deletePost(String publicId) {
        log.warn("Admin DELETING post Public ID: {}", publicId);
        postRepository.findByPublicId(publicId).ifPresent(post -> {
            if (post.getMediaUrl() != null && post.getMediaUrl().startsWith("/api/media/files/")) {
                try {
                    String fileName = post.getMediaUrl().substring("/api/media/files/".length());
                    java.nio.file.Path filePath = java.nio.file.Paths.get("uploads").resolve(fileName);
                    java.nio.file.Files.deleteIfExists(filePath);
                } catch (Exception e) {
                    log.error("Failed to delete media file for post " + publicId, e);
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
                .banReason(user.getBanReason())
                .bannedUntil(user.getBannedUntil())
                .avatarUrl(user.getProfile() != null ? user.getProfile().getAvatarUrl() : null)
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Transactional
    public void updateDisplayName(String username, String displayName, String callerPublicId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User caller = userRepository.findByPublicId(callerPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Caller not found"));

        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.SUPER_ADMIN) {
            if (caller.getRole() != User.Role.SUPER_ADMIN) {
                throw new UnauthorizedActionException("Only SUPER_ADMIN can modify administrator profiles.");
            }
        }

        log.info("Admin {} updating display name for user {}: {}", caller.getUsername(), username, displayName);
        UserProfile profile = userProfileRepository.findByUser(user)
                .orElse(UserProfile.builder().user(user).build());
        profile.setDisplayName(displayName);
        userProfileRepository.save(profile);
    }

    @Transactional
    public void updateUsername(String oldUsername, String newUsername, String callerPublicId) {
        User user = userRepository.findByUsername(oldUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User caller = userRepository.findByPublicId(callerPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Caller not found"));

        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.SUPER_ADMIN) {
            if (caller.getRole() != User.Role.SUPER_ADMIN) {
                throw new UnauthorizedActionException("Only SUPER_ADMIN can modify administrator profiles.");
            }
        }

        log.info("Admin {} updating username for user {}: {}", caller.getUsername(), oldUsername, newUsername);
        
        if (newUsername == null || newUsername.isBlank()) {
            throw new BadRequestException("Username cannot be empty");
        }
        
        newUsername = newUsername.trim();
        
        if (newUsername.length() < 3 || newUsername.length() > 30) {
            throw new BadRequestException("Username must be between 3 and 30 characters.");
        }
        if (!newUsername.matches("^[a-zA-Z0-9_-]+$")) {
            throw new BadRequestException("Username can only contain letters, numbers, underscores, and hyphens.");
        }
        
        if (!user.getUsername().equals(newUsername) && userRepository.existsByUsername(newUsername)) {
            throw new BadRequestException("Username is already taken.");
        }
        
        user.setUsername(newUsername);
        userRepository.save(user);
    }
}
