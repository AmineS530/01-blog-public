package com.zero1blog.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Rate limiter for user interactions: likes, comments, and follows.
 * Prevents spamming and returns HTTP 429 when limits are exceeded.
 */
@Component
@Slf4j
public class InteractionRateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Rate limit POST requests for these endpoints
        if (!"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String actionType = null;
        int limitAmount = 0;
        Duration limitPeriod = null;

        // Map endpoints to action type and limits
        if (path.matches("^/api/posts/[^/]+/likes$") || path.matches("^/api/comments/\\d+/likes$")) {
            actionType = "LIKE";
            limitAmount = 10; // 10 likes per 10 seconds
            limitPeriod = Duration.ofSeconds(10);
        } else if (path.matches("^/api/posts/[^/]+/comments$")) {
            actionType = "COMMENT";
            limitAmount = 5; // 5 comments per 10 seconds
            limitPeriod = Duration.ofSeconds(10);
        } else if (path.matches("^/api/profiles/[^/]+/follow$")) {
            actionType = "FOLLOW";
            limitAmount = 5; // 5 follows per 10 seconds
            limitPeriod = Duration.ofSeconds(10);
        }

        if (actionType == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Identify requester: prioritize authenticated publicId, fallback to IP address
        String userKey = resolveUserKey(request);
        String bucketKey = userKey + ":" + actionType;

        final int finalLimitAmount = limitAmount;
        final Duration finalLimitPeriod = limitPeriod;
        Bucket bucket = buckets.get(bucketKey, k -> newBucket(finalLimitAmount, finalLimitPeriod));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for key: {} on action: {}", bucketKey, actionType);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
        }
    }

    private Bucket newBucket(int limitAmount, Duration period) {
        Bandwidth limit = Bandwidth.classic(
                limitAmount,
                Refill.greedy(limitAmount, period)
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveUserKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails) {
            return ((UserDetails) auth.getPrincipal()).getUsername();
        }
        
        // Fallback to client IP
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
