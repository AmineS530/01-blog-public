package com.zero1blog.backend.controller;

import com.zero1blog.backend.dto.ProfileResponse;
import com.zero1blog.backend.dto.ProfileUpdateRequest;
import com.zero1blog.backend.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{username}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String username,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        String currentUserPublicId = userDetails != null ? userDetails.getUsername() : null; // In our JwtAuthFilter, we use publicId as the username field in UserDetails
        return ResponseEntity.ok(profileService.getProfile(username, currentUserPublicId));
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateProfile(@RequestBody ProfileUpdateRequest request,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(profileService.updateProfile(userDetails.getUsername(), request));
    }

    @PostMapping("/{username}/follow")
    public ResponseEntity<Void> toggleFollow(@PathVariable String username,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        profileService.toggleFollow(username, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{username}/block")
    public ResponseEntity<Void> toggleBlock(@PathVariable String username,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        profileService.toggleBlock(username, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
