package com.zero1blog.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import com.zero1blog.backend.dto.AuthResponse;
import com.zero1blog.backend.dto.LoginRequest;
import com.zero1blog.backend.dto.RegisterRequest;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.model.UserCredentials;
import com.zero1blog.backend.model.UserProfile;
import com.zero1blog.backend.repository.UserCredentialsRepository;
import com.zero1blog.backend.repository.UserProfileRepository;
import com.zero1blog.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.getUsername());
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: Email already in use: {}", request.getEmail());
            throw new RuntimeException("Email already in use");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration failed: Username already taken: {}", request.getUsername());
            throw new RuntimeException("Username already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .role(User.Role.USER)
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
        String token = jwtService.generateToken(user.getPublicId(), user.getRole().name(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for username/email: {}", request.getUsernameOrEmail());
        User user = userRepository.findByEmail(request.getUsernameOrEmail())
                .orElseGet(() -> userRepository.findByUsername(request.getUsernameOrEmail())
                        .orElseThrow(() -> {
                            log.warn("Login failed: User not found: {}", request.getUsernameOrEmail());
                            return new RuntimeException("Invalid credentials");
                        }));

        UserCredentials credentials = userCredentialsRepository.findByUser(user)
                .orElseThrow(() -> {
                    log.error("Integrity error: Credentials missing for user: {}", user.getUsername());
                    return new RuntimeException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.getPassword(), credentials.getPasswordHash())) {
            log.warn("Login failed: Invalid password for user: {}", user.getUsername());
            throw new RuntimeException("Invalid credentials");
        }

        if (user.isBanned()) {
            log.warn("Login failed: User is banned: {}", user.getUsername());
            throw new RuntimeException("Your account has been banned");
        }

        log.info("User logged in successfully: {}", user.getUsername());
        String token = jwtService.generateToken(user.getPublicId(), user.getRole().name(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}