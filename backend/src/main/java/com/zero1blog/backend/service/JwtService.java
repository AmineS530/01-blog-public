package com.zero1blog.backend.service;

import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Service component responsible for JSON Web Tokens lifecycle management.
 * <p>
 * This utility generates cryptographically signed JWT strings, validates token signatures 
 * against a configured application secret, and parses claims to extract authenticated user criteria
 * (such as public ID, username, and role).
 * </p>
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * Helper mapping the raw secret byte configuration to a secure HMAC-SHA key.
     *
     * @return cryptographically secured HMAC SecretKey instance.
     */
    private javax.crypto.SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Generates a signed, structured JSON Web Token for an authenticated user session.
     *
     * @param publicId the user's system-wide unique public identifier (mapped to the token subject claim).
     * @param role     the authorization role classification.
     * @param username the user's display username.
     * @return the serialized JWT string.
     */
    public String generateToken(String publicId, String role, String username) {
        return Jwts.builder()
                .subject(publicId)
                .claim("role", role)
                .claim("username",username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the subject claim (the user's public ID) from a signed token.
     */
    public String extractPublicId(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the custom role claim from a signed token.
     */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Validates whether a token signature is cryptographically valid and not expired.
     *
     * @param token raw JWT string to evaluate.
     * @return {@code true} if valid, {@code false} if parsing triggers a signature discrepancy or expiration.
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Centralized parser checking the token signature against the cryptographically secure signing key.
     *
     * @param token JWT string to parse.
     * @return the set of verified claims.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}