package com.zero1blog.backend.controller;

import com.zero1blog.backend.dto.ReportRequest;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<ReportResponse> createReport(@RequestBody ReportRequest request, Authentication authentication) {
        return ResponseEntity.ok(reportService.createReport(request, authentication.getName()));
    }
}
