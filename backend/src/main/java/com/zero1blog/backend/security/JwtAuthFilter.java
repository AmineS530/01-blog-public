package com.zero1blog.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zero1blog.backend.repository.UserRepository;
import com.zero1blog.backend.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (jwtService.isTokenValid(token)) {
            String publicId = jwtService.extractPublicId(token);
            
            var userOpt = userRepository.findByPublicId(publicId);
            if (userOpt.isEmpty()) {
                log.warn("JWT Valid but User not found in database: {}", publicId);
                filterChain.doFilter(request, response);
                return;
            }

            if (userOpt.get().isBanned()) {
                log.warn("Banned user attempted access: {}", publicId);
                filterChain.doFilter(request, response);
                return;
            }

            String role = userOpt.get().getRole().name();
    
            User userDetails = new User(
                    publicId,
                    "",
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
    
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
            
            log.debug("User {} authenticated with authorities: {}", publicId, userDetails.getAuthorities());
    
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}