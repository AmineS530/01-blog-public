package com.zero1blog.backend.controller;

import com.zero1blog.backend.dto.AdminStatsResponse;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReportResponse>> getReports(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(adminService.getReports(status, page, limit));
    }

    @PostMapping("/reports/{id}/resolve")
    public ResponseEntity<Void> resolveReport(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        adminService.resolveReport(id, body.get("action"), body.get("note"));
        return ResponseEntity.ok().build();
    }
}
