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
                .orElseThrow(() -> new RuntimeException("Reporter not found"));

        Report report = Report.builder()
                .reason(request.getReason())
                .reporter(reporter)
                .build();

        if (request.getTargetUserId() != null) {
            User targetUser = userRepository.findById(request.getTargetUserId())
                    .orElseThrow(() -> new RuntimeException("Target user not found"));
            report.setTargetUser(targetUser);
        }

        if (request.getTargetPostId() != null) {
            Post targetPost = postRepository.findById(request.getTargetPostId())
                    .orElseThrow(() -> new RuntimeException("Target post not found"));
            report.setTargetPost(targetPost);
        }

        if (request.getTargetCommentId() != null) {
            Comment targetComment = commentRepository.findById(request.getTargetCommentId())
                    .orElseThrow(() -> new RuntimeException("Target comment not found"));
            report.setTargetComment(targetComment);
        }

        Report saved = reportRepository.save(report);
        return toResponse(saved);
    }

    public ReportResponse toResponse(Report report) {
        String targetType = null;
        Long targetId = null;

        if (report.getTargetUser() != null) {
            targetType = "USER";
            targetId = report.getTargetUser().getId();
        } else if (report.getTargetPost() != null) {
            targetType = "POST";
            targetId = report.getTargetPost().getId();
        } else if (report.getTargetComment() != null) {
            targetType = "COMMENT";
            targetId = report.getTargetComment().getId();
        }

        return ReportResponse.builder()
                .id(report.getId())
                .reason(report.getReason())
                .status(report.getStatus())
                .reporterUsername(report.getReporter().getUsername())
                .targetUsername(report.getTargetUser() != null ? report.getTargetUser().getUsername() : null)
                .targetPostId(report.getTargetPost() != null ? report.getTargetPost().getId() : null)
                .targetCommentId(report.getTargetComment() != null ? report.getTargetComment().getId() : null)
                .targetType(targetType)
                .targetId(targetId)
                .createdAt(report.getCreatedAt())
                .build();
    }
}
