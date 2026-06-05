package com.zero1blog.backend.service;

import com.zero1blog.backend.dto.ReportRequest;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.model.Comment;
import com.zero1blog.backend.model.Post;
import com.zero1blog.backend.model.Report;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.CommentRepository;
import com.zero1blog.backend.repository.PostRepository;
import com.zero1blog.backend.repository.ReportRepository;
import com.zero1blog.backend.repository.UserRepository;
import com.zero1blog.backend.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public ReportService(ReportRepository reportRepository, UserRepository userRepository, PostRepository postRepository, CommentRepository commentRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public ReportResponse createReport(ReportRequest request, String reporterPublicId) {
        User reporter = userRepository.findByPublicId(reporterPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporter not found"));

        // BUG FIX: Ensure exactly one target is provided
        int targetCount = 0;
        if (request.getTargetUserId() != null) targetCount++;
        if (request.getTargetPostId() != null) targetCount++;
        if (request.getTargetCommentId() != null) targetCount++;

        if (targetCount != 1) {
            throw new BadRequestException("Report must have exactly one target (User, Post, or Comment)");
        }

        Report report = Report.builder()
                .reason(request.getReason())
                .reporter(reporter)
                .build();

        if (request.getTargetUserId() != null) {
            User targetUser = userRepository.findById(request.getTargetUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));
            
            // BUG FIX: Prevent self-reporting
            if (targetUser.getId().equals(reporter.getId())) {
                throw new BadRequestException("Cannot report yourself");
            }
            report.setTargetUser(targetUser);
        }

        if (request.getTargetPostId() != null) {
            Post targetPost = postRepository.findById(request.getTargetPostId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target post not found"));
            
            if (targetPost.getAuthor().getId().equals(reporter.getId())) {
                throw new BadRequestException("Cannot report your own post");
            }
            report.setTargetPost(targetPost);
        }

        if (request.getTargetCommentId() != null) {
            Comment targetComment = commentRepository.findById(request.getTargetCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target comment not found"));
            
            if (targetComment.getAuthor().getId().equals(reporter.getId())) {
                throw new BadRequestException("Cannot report your own comment");
            }
            report.setTargetComment(targetComment);
        }

        Report saved = reportRepository.save(report);
        return toResponse(saved);
    }

    public ReportResponse toResponse(Report report) {
        String targetType = null;
        Long targetId = null;
        String targetUsername = null;
        Long targetPostId = null;
        Long targetCommentId = null;

        if (report.getTargetUser() != null) {
            targetType = "USER";
            targetId = report.getTargetUser().getId();
            targetUsername = report.getTargetUser().getUsername();
        } else if (report.getTargetPost() != null) {
            targetType = "POST";
            targetId = report.getTargetPost().getId();
            targetUsername = report.getTargetPost().getAuthor().getUsername();
            targetPostId = report.getTargetPost().getId();
        } else if (report.getTargetComment() != null) {
            targetType = "COMMENT";
            targetId = report.getTargetComment().getId();
            targetUsername = report.getTargetComment().getAuthor().getUsername();
            targetCommentId = report.getTargetComment().getId();
            targetPostId = report.getTargetComment().getPost().getId();
        }

        return ReportResponse.builder()
                .id(report.getId())
                .reason(report.getReason())
                .status(report.getStatus())
                .reporterUsername(report.getReporter().getUsername())
                .targetUsername(targetUsername)
                .targetPostId(targetPostId)
                .targetCommentId(targetCommentId)
                .targetType(targetType)
                .targetId(targetId)
                .createdAt(report.getCreatedAt())
                .build();
    }
}
