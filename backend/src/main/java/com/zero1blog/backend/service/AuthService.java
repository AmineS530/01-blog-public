package com.zero1blog.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zero1blog.backend.dto.AuthResponse;
import com.zero1blog.backend.dto.LoginRequest;
import com.zero1blog.backend.dto.RegisterRequest;
import com.zero1blog.backend.exception.BadRequestException;
import com.zero1blog.backend.exception.UnauthorizedActionException;
import com.zero1blog.backend.exception.UsernameChangeCooldownException;
import com.zero1blog.backend.model.RefreshToken;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.model.UserCredentials;
import com.zero1blog.backend.model.UserProfile;
import com.zero1blog.backend.repository.UserCredentialsRepository;
import com.zero1blog.backend.repository.UserProfileRepository;
import com.zero1blog.backend.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

import com.zero1blog.backend.dto.ChangePasswordRequest;
import com.zero1blog.backend.dto.ChangeUsernameRequest;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Minimum elapsed time between two consecutive username changes for any
     * user. Configured via {@code app.username-change-cooldown} (in seconds).
     * Default 14 days (1_209_600s). A user who has never changed their
     * username ({@code usernameChangedAt == null}) is allowed to change it
     * immediately; subsequent changes are gated by this cooldown.
     */
    private final Duration usernameChangeCooldown;

    public AuthService(
            UserRepository userRepository,
            UserCredentialsRepository userCredentialsRepository,
            UserProfileRepository userProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            @Value("${app.username-change-cooldown:1209600}") long usernameChangeCooldownSeconds) {
        this.userRepository = userRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.usernameChangeCooldown = Duration.ofSeconds(usernameChangeCooldownSeconds);
    }

    @Transactional  // Fix #8: roll back all three saves if any step fails
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.getUsername());
        validateUsernamePolicy(request.getUsername());
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: Email already in use: {}", request.getEmail());
            throw new BadRequestException("Email already in use");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration failed: Username already taken: {}", request.getUsername());
            throw new BadRequestException("Username already taken");
        }

        User.Role role = userRepository.count() == 0 ? User.Role.SUPER_ADMIN : User.Role.USER;
        log.info("Assigning role {} to new user: {}", role, request.getUsername());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .role(role)
                .build();

        userRepository.save(user);

        UserCredentials credentials = UserCredentials.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userCredentialsRepository.save(credentials);

        UserProfile profile = UserProfile.builder()
                .user(user)
                .build();
        userProfileRepository.save(profile);

        log.info("User registered successfully: {}", user.getUsername());
        String token = jwtService.generateToken(user.getPublicId(), user.getRole().name());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for username/email: {}", request.getUsernameOrEmail());
        User user = userRepository.findByEmail(request.getUsernameOrEmail())
                .orElseGet(() -> userRepository.findByUsername(request.getUsernameOrEmail())
                        .orElseThrow(() -> {
                            log.warn("Login failed: User not found: {}", request.getUsernameOrEmail());
                            return new UnauthorizedActionException("Invalid credentials");
                        }));

        UserCredentials credentials = userCredentialsRepository.findByUser(user)
                .orElseThrow(() -> {
                    log.error("Integrity error: Credentials missing for user: {}", user.getUsername());
                    return new UnauthorizedActionException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.getPassword(), credentials.getPasswordHash())) {
            log.warn("Login failed: Invalid password for user: {}", user.getUsername());
            throw new UnauthorizedActionException("Invalid credentials");
        }

        if (user.isBanned()) {
            log.warn("Login failed: User is banned: {}", user.getUsername());
            throw new UnauthorizedActionException("Your account has been banned");
        }

        log.info("User logged in successfully: {}", user.getUsername());
        String token = jwtService.generateToken(user.getPublicId(), user.getRole().name());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    public void logout(String rawRefreshToken) {
        // Invalidate server-side token so it cannot be reused even if the cookie was captured before logout
        try {
            RefreshToken token = refreshTokenService.findByToken(rawRefreshToken);
            refreshTokenService.deleteByUser(token.getUser());
            log.info("User logged out, refresh token invalidated for user: {}", token.getUser().getUsername());
        } catch (Exception e) {
            // Token already expired or not found — safe to ignore, logout proceeds
            log.warn("Logout called with unrecognised or already-expired refresh token");
        }
    }

    public AuthResponse refreshToken(String rawRefreshToken, String rawAccessToken) {
        log.info("Token refresh attempt");
        RefreshToken refreshToken = refreshTokenService.findByToken(rawRefreshToken);
        refreshTokenService.verifyExpiration(refreshToken);

        User user = refreshToken.getUser();

        // Perform access token checks if it was supplied
        if (rawAccessToken != null && !rawAccessToken.isBlank()) {
            try {
                io.jsonwebtoken.Claims claims;
                try {
                    claims = jwtService.parseClaims(rawAccessToken);
                    // If parsing succeeds, it means the token is valid and not expired.
                    // Under security best practices, we only refresh expired access tokens.
                    log.warn("Refresh attempt with non-expired access token for user {}", user.getUsername());
                    throw new BadRequestException("Access token is not expired");
                } catch (io.jsonwebtoken.ExpiredJwtException e) {
                    claims = e.getClaims();
                }

                String publicIdFromAccessToken = claims.getSubject();
                if (publicIdFromAccessToken == null || !publicIdFromAccessToken.equals(user.getPublicId())) {
                    log.warn("Access token subject ({}) does not match refresh token user ({})", publicIdFromAccessToken, user.getPublicId());
                    throw new UnauthorizedActionException("Invalid session mapping");
                }
            } catch (io.jsonwebtoken.security.SignatureException e) {
                log.warn("Refresh token refresh rejected: invalid access token signature");
                throw new UnauthorizedActionException("Invalid token signature");
            } catch (io.jsonwebtoken.MalformedJwtException e) {
                log.warn("Refresh token refresh rejected: malformed access token");
                throw new UnauthorizedActionException("Malformed token");
            } catch (io.jsonwebtoken.IncorrectClaimException e) {
                log.warn("Refresh token refresh rejected: invalid token claims");
                throw new UnauthorizedActionException("Invalid token issuer or audience");
            } catch (io.jsonwebtoken.UnsupportedJwtException e) {
                log.warn("Refresh token refresh rejected: unsupported token algorithm");
                throw new UnauthorizedActionException("Unsupported token algorithm");
            } catch (io.jsonwebtoken.JwtException e) {
                log.warn("Refresh token refresh rejected: invalid token");
                throw new UnauthorizedActionException("Invalid token");
            }
        }

        String accessToken = jwtService.generateToken(user.getPublicId(), user.getRole().name());
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);

        log.info("Token refreshed successfully for user: {}", user.getUsername());
        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public void changePassword(String publicId, ChangePasswordRequest request) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        UserCredentials credentials = userCredentialsRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Credentials not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), credentials.getPasswordHash())) {
            throw new BadRequestException("Incorrect current password");
        }

        credentials.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userCredentialsRepository.save(credentials);
        log.info("Password changed successfully for user: {}", user.getUsername());
    }

    @Transactional
    public String changeUsername(String publicId, ChangeUsernameRequest request) {
        String newUsername = request.getNewUsername().trim();
        validateUsernamePolicy(newUsername);

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Cooldown policy: the configured minimum elapsed time between two
        // consecutive username changes. Null usernameChangedAt means the user
        // has never changed their username — they get one free first change.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastChangedAt = user.getUsernameChangedAt();
        if (lastChangedAt != null) {
            LocalDateTime nextAllowedAt = lastChangedAt.plus(usernameChangeCooldown);
            if (now.isBefore(nextAllowedAt)) {
                long minutesRemaining = java.time.temporal.ChronoUnit.MINUTES.between(now, nextAllowedAt);
                long daysRemaining = minutesRemaining / (60 * 24);
                long hoursRemaining = (minutesRemaining / 60) % 24;
                log.warn("Username change for user {} rejected: {} days {} hours until allowed",
                        publicId, daysRemaining, hoursRemaining);
                throw new UsernameChangeCooldownException(
                        String.format(
                                "You can change your username again in %d days, %d hours.",
                                daysRemaining, hoursRemaining),
                        nextAllowedAt);
            }
        }

        if (userRepository.existsByUsername(newUsername)) {
            throw new BadRequestException("Username already taken");
        }

        String oldUsername = user.getUsername();
        user.setUsername(newUsername);
        user.setUsernameChangedAt(now);
        userRepository.save(user);
        log.info("Username changed successfully from {} to {} for user: {}", oldUsername, newUsername, publicId);
        return newUsername;
    }

    /**
     * Exposed for the {@code GET /api/auth/username-cooldown} endpoint so the
     * UI can render its own countdown without parsing a 400 message.
     */
    public long getUsernameChangeCooldownSeconds() {
        return usernameChangeCooldown.getSeconds();
    }

    private static final java.util.regex.Pattern USERNAME_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");

    private void validateUsernamePolicy(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new BadRequestException("Username must be between 3 and 30 characters and contain only letters, numbers, underscores, and hyphens");
        }
    }
}