package com.zero1blog.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.zero1blog.backend.dto.ReportRequest;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.exception.BadRequestException;
import com.zero1blog.backend.exception.ResourceNotFoundException;
import com.zero1blog.backend.model.Comment;
import com.zero1blog.backend.model.Post;
import com.zero1blog.backend.model.Report;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.CommentRepository;
import com.zero1blog.backend.repository.PostRepository;
import com.zero1blog.backend.repository.ReportRepository;
import com.zero1blog.backend.repository.UserRepository;
import com.zero1blog.backend.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void createReportSuccess() {
        User reporter = new User();
        reporter.setId(1L);
        reporter.setUsername("reporter");
        reporter.setPublicId("reporter-pub");

        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("target");

        ReportRequest request = new ReportRequest();
        request.setReason("Spam behavior");
        request.setTargetUserId(2L);

        Report savedReport = Report.builder()
                .id(10L)
                .reason("Spam behavior")
                .reporter(reporter)
                .targetUser(targetUser)
                .status("pending")
                .build();

        when(userRepository.findByPublicId("reporter-pub")).thenReturn(Optional.of(reporter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(reportRepository.save(any(Report.class))).thenReturn(savedReport);

        ReportResponse response = reportService.createReport(request, "reporter-pub");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getReason()).isEqualTo("Spam behavior");
        assertThat(response.getTargetType()).isEqualTo("USER");
        assertThat(response.getTargetUsername()).isEqualTo("target");
    }

    @Test
    void createReportMissingReasonThrowsException() {
        User reporter = new User();
        reporter.setId(1L);
        reporter.setPublicId("reporter-pub");

        ReportRequest request = new ReportRequest();
        request.setReason("   "); // Blank reason
        request.setTargetUserId(2L);

        when(userRepository.findByPublicId("reporter-pub")).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.createReport(request, "reporter-pub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Reason is required");
    }

    @Test
    void createReportMultipleTargetsThrowsException() {
        User reporter = new User();
        reporter.setId(1L);
        reporter.setPublicId("reporter-pub");

        ReportRequest request = new ReportRequest();
        request.setReason("Spam");
        request.setTargetUserId(2L);
        request.setTargetPostId("post-pub-id"); // Multiple targets

        when(userRepository.findByPublicId("reporter-pub")).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.createReport(request, "reporter-pub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exactly one target");
    }

    @Test
    void createReportSelfReportingThrowsException() {
        User reporter = new User();
        reporter.setId(1L);
        reporter.setPublicId("reporter-pub");

        ReportRequest request = new ReportRequest();
        request.setReason("Spam");
        request.setTargetUserId(1L); // Self reporting

        when(userRepository.findByPublicId("reporter-pub")).thenReturn(Optional.of(reporter));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.createReport(request, "reporter-pub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot report yourself");
    }
}
