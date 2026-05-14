package com.zero1blog.backend.service;

import com.zero1blog.backend.dto.AdminStatsResponse;
import com.zero1blog.backend.dto.ProfileResponse;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.model.Report;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

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
        return AdminStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalPosts(postRepository.count())
                .totalComments(commentRepository.count())
                .totalReports(reportRepository.count())
                .pendingReports(reportRepository.countByStatus("pending"))
                .bannedUsers(userRepository.countByIsBanned(true))
                .build();
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getReports(String status, int page, int limit) {
        Page<Report> reports = reportRepository.findByStatus(status, PageRequest.of(page, limit));
        return reports.getContent().stream()
                .map(reportService::toResponse)
                .collect(Collectors.toList());
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
    public void banUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
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
}
