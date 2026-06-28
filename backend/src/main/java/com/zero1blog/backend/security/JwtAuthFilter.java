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

/**
 * Custom JWT Security Interceptor.
 * <p>
 * Implements {@link OncePerRequestFilter} to intercept every inbound HTTP request.
 * It attempts to extract a JSON Web Token from the standard {@code Authorization} request header,
 * validates the signature, performs security checks (e.g., verifying user ban status), 
 * and binds the resolved user role authorities into Spring Security's context holder.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * Intercepts HTTP requests to resolve JWT identity structures.
     * <p>
     * Filtering lifecycle:
     * 1. Evaluates presence and structure of the {@code Authorization} header. If missing or not prefixed
     *    with "Bearer ", it passes control down the filter chain without authenticating.
     * 2. Extracts and validates the token signature using {@link JwtService}.
     * 3. Retrieves the active user record. If the user does not exist or has been flagged as banned, 
     *    access is blocked, preventing illegal operations from banned accounts.
     * 4. Converts the resolved role string into a Spring standard {@link SimpleGrantedAuthority} prefixed with "ROLE_".
     * 5. Instantiates a {@link UsernamePasswordAuthenticationToken} and stores it in the active
     *    {@link SecurityContextHolder} to grant system-wide execution permissions for this thread context.
     * </p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Validate presence of Bearer Authorization header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String requestURI = request.getRequestURI();
        boolean isRefreshRoute = requestURI.endsWith("/api/auth/refresh");

        try {
            io.jsonwebtoken.Claims claims = jwtService.parseClaims(token);
            String publicId = claims.getSubject();
            
            var userOpt = userRepository.findByPublicId(publicId);
            if (userOpt.isEmpty()) {
                log.warn("JWT Valid but User not found in database: {}", publicId);
                writeErrorResponse(response, "User not found", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            com.zero1blog.backend.model.User dbUser = userOpt.get();
            if (dbUser.getBannedUntil() != null && dbUser.getBannedUntil().isBefore(java.time.LocalDateTime.now())) {
                dbUser.setBanned(false);
                dbUser.setBanReason(null);
                dbUser.setBannedUntil(null);
                userRepository.save(dbUser);
            }

            // Security constraint: Abort authenticated session mapping if the user has been banned
            if (dbUser.isBanned()) {
                log.warn("Banned user attempted access: {}", publicId);
                writeErrorResponse(response, "Your account has been banned: " + (dbUser.getBanReason() != null ? dbUser.getBanReason() : "No reason provided"), HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String role = userOpt.get().getRole().name();
    
            // Map the resolved role to Spring Security's expected authority string format
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
    
            // Inject authentication token into current Spring security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            if (isRefreshRoute) {
                log.debug("Allowing expired JWT to proceed to refresh endpoint");
            } else {
                log.warn("JWT Expired: {}", e.getMessage());
                writeErrorResponse(response, "Token expired", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("JWT Signature invalid: {}", e.getMessage());
            writeErrorResponse(response, "Invalid token signature", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.warn("JWT Malformed: {}", e.getMessage());
            writeErrorResponse(response, "Malformed token", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (io.jsonwebtoken.IncorrectClaimException e) {
            log.warn("JWT Claims invalid: {}", e.getMessage());
            writeErrorResponse(response, "Invalid token issuer or audience", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            log.warn("JWT Unsupported: {}", e.getMessage());
            writeErrorResponse(response, "Unsupported token algorithm", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("JWT Invalid: {}", e.getMessage());
            writeErrorResponse(response, "Invalid token", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        java.util.Map<String, Object> body = java.util.Map.of(
            "error", message,
            "status", status,
            "timestamp", java.time.LocalDateTime.now().toString()
        );
        
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
        response.getWriter().write(json);
    }
}