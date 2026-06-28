package com.zero1blog.backend.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zero1blog.backend.dto.AuthResponse;
import com.zero1blog.backend.dto.LoginRequest;
import com.zero1blog.backend.dto.RefreshTokenRequest;
import com.zero1blog.backend.dto.RegisterRequest;
import com.zero1blog.backend.exception.BadRequestException;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.UserRepository;
import com.zero1blog.backend.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.zero1blog.backend.dto.ChangePasswordRequest;
import com.zero1blog.backend.dto.ChangeUsernameRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        ResponseCookie cookie = createCookie(response.getRefreshToken());
        response.setRefreshToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        ResponseCookie cookie = createCookie(response.getRefreshToken());
        response.setRefreshToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new com.zero1blog.backend.exception.BadRequestException("Refresh token is missing");
        }
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        AuthResponse response = authService.refreshToken(request);
        ResponseCookie cookie = createCookie(response.getRefreshToken());
        response.setRefreshToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        // Fix #13: invalidate the server-side token so it can't be reused even if the cookie was captured
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        authService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-username")
    public ResponseEntity<java.util.Map<String, String>> changeUsername(
            @Valid @RequestBody ChangeUsernameRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        String newUsername = authService.changeUsername(userDetails.getUsername(), request);
        return ResponseEntity.ok(java.util.Map.of("username", newUsername));
    }

    /**
     * Returns the configured username-change cooldown (in seconds) and the
     * caller's next allowed change timestamp. Used by the settings UI to
     * render a countdown and disable the submit button while the cooldown
     * is active.
     *
     * LinkedHashMap preserves field order in the JSON response so the FE can
     * rely on a stable shape during testing/devtools inspection.
     */
    @GetMapping("/username-cooldown")
    public ResponseEntity<Map<String, Object>> usernameCooldown(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findByPublicId(userDetails.getUsername())
                .orElseThrow(() -> new BadRequestException("User not found"));

        long cooldownSeconds = authService.getUsernameChangeCooldownSeconds();
        LocalDateTime lastChangedAt = user.getUsernameChangedAt();
        LocalDateTime nextAllowedAt = lastChangedAt != null
                ? lastChangedAt.plusSeconds(cooldownSeconds)
                : null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cooldownSeconds", cooldownSeconds);
        body.put("lastChangedAt", lastChangedAt != null ? lastChangedAt.toString() : null);
        body.put("nextAllowedAt", nextAllowedAt != null ? nextAllowedAt.toString() : null);
        return ResponseEntity.ok(body);
    }

    private ResponseCookie createCookie(String token) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(cookieSecure)   // Fix #3: driven by config, true in production
                .path("/api/auth")
                .maxAge(604800) // 7 days
                .sameSite("Lax")
                .build();
    }
}