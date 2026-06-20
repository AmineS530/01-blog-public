package com.zero1blog.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zero1blog.backend.dto.AuthResponse;
import com.zero1blog.backend.dto.LoginRequest;
import com.zero1blog.backend.dto.RegisterRequest;
import com.zero1blog.backend.exception.BadRequestException;
import com.zero1blog.backend.exception.UnauthorizedActionException;
import com.zero1blog.backend.model.RefreshToken;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.model.UserCredentials;
import com.zero1blog.backend.model.UserProfile;
import com.zero1blog.backend.repository.UserCredentialsRepository;
import com.zero1blog.backend.repository.UserProfileRepository;
import com.zero1blog.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional  // Fix #8: roll back all three saves if any step fails
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.getUsername());
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

    public AuthResponse refreshToken(com.zero1blog.backend.dto.RefreshTokenRequest request) {
        log.info("Token refresh attempt");
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken());
        refreshTokenService.verifyExpiration(refreshToken);

        User user = refreshToken.getUser();
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
}