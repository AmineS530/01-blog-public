package com.zero1blog.backend.exception;

import java.time.LocalDateTime;

/**
 * Thrown when a user attempts to change their username before the configured
 * cooldown period has elapsed since their last change.
 *
 * Carries {@code nextAllowedAt} so the API response can tell the frontend
 * exactly when the user is allowed to change their username again.
 */
public class UsernameChangeCooldownException extends RuntimeException {

    private final LocalDateTime nextAllowedAt;

    public UsernameChangeCooldownException(String message, LocalDateTime nextAllowedAt) {
        super(message);
        this.nextAllowedAt = nextAllowedAt;
    }

    public LocalDateTime getNextAllowedAt() {
        return nextAllowedAt;
    }
}
