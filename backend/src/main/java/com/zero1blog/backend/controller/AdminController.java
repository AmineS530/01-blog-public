package com.zero1blog.backend.controller;

import com.zero1blog.backend.dto.AdminStatsResponse;
import com.zero1blog.backend.dto.ReportResponse;
import com.zero1blog.backend.dto.UserAdminResponse;
import com.zero1blog.backend.service.AdminService;
import org.springframework.data.domain.Page;
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

    @GetMapping("/users")
    public ResponseEntity<Page<UserAdminResponse>> getUsers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(adminService.getUsers(query, page, limit));
    }

    @PostMapping("/users/{username}/role")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable String username,
            @RequestBody Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        adminService.updateUserRole(username, body.get("role"), userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{username}/toggle-ban")
    public ResponseEntity<Void> toggleBan(
            @PathVariable String username,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        adminService.toggleBan(username, userDetails.getUsername());
        return ResponseEntity.ok().build();
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

    @PostMapping("/users/{username}/ban")
    public ResponseEntity<Void> banUser(
            @PathVariable String username,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        adminService.banUser(username, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        adminService.deletePost(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        adminService.deleteComment(id);
        return ResponseEntity.ok().build();
    }
}
