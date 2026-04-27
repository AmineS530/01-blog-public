package com.zero1blog.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.zero1blog.backend.dto.AuthResponse;
import com.zero1blog.backend.dto.LoginRequest;
import com.zero1blog.backend.dto.RegisterRequest;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.model.UserCredentials;
import com.zero1blog.backend.repository.UserCredentialsRepository;
import com.zero1blog.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
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

        String token = jwtService.generateToken(user.getPublicId(), user.getRole().name(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        UserCredentials credentials = userCredentialsRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), credentials.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getPublicId(), user.getRole().name(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}