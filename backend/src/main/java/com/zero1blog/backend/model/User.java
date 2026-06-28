package com.zero1blog.backend.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_is_banned", columnList = "is_banned")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String publicId;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile profile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserCredentials credentials;

    public enum Role {
        USER, ADMIN, SUPER_ADMIN
    }

    public String getDisplayName() {
        if (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().trim().isEmpty()) {
            return profile.getDisplayName();
        }
        return username;
    }

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean isBanned = false;

    @Column(name = "ban_reason")
    private String banReason;

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;

    public boolean isBanned() {
        if (!isBanned) {
            return false;
        }
        if (bannedUntil != null && bannedUntil.isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    /**
     * Timestamp of the last username change for this user. Null if the user has
     * never changed their username. Used to enforce a cooldown period between
     * successive changes — see AuthService.changeUsername and the
     * app.username-change-cooldown property.
     */
    @Column
    private LocalDateTime usernameChangedAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}