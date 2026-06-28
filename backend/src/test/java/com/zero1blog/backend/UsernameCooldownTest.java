package com.zero1blog.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.zero1blog.backend.dto.ChangeUsernameRequest;
import com.zero1blog.backend.exception.BadRequestException;
import com.zero1blog.backend.exception.UsernameChangeCooldownException;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.UserCredentialsRepository;
import com.zero1blog.backend.repository.UserProfileRepository;
import com.zero1blog.backend.repository.UserRepository;
import com.zero1blog.backend.service.AuthService;
import com.zero1blog.backend.service.JwtService;
import com.zero1blog.backend.service.RefreshTokenService;

/**
 * Unit-tests for AuthService.changeUsername's cooldown policy.
 *
 * Uses Mockito to avoid touching the live database — the cooldown check is
 * pure date arithmetic, so a fake repository is enough to drive the branches
 * we care about (first change allowed, within-cooldown rejected,
 * past-cooldown allowed).
 *
 * The companion AuthService instance is built with small cooldown values
 * (10–60 seconds) so the cooldown branches can be exercised without waiting
 * two weeks for real-time arithmetic to elapse.
 */
@ExtendWith(MockitoExtension.class)
class UsernameCooldownTest {

    @Mock UserRepository userRepository;
    @Mock UserCredentialsRepository userCredentialsRepository;
    @Mock UserProfileRepository userProfileRepository;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /**
     * Builds an AuthService with the supplied cooldown. Tests pass small numbers
     * (10–60 seconds) so the cooldown branches can be exercised without waiting
     * two weeks for real-time arithmetic to elapse.
     */
    private AuthService buildService(long cooldownSeconds) {
        return new AuthService(
                userRepository,
                userCredentialsRepository,
                userProfileRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                cooldownSeconds);
    }

    private User userWithLastChangedAt(LocalDateTime lastChangedAt) {
        User u = User.builder()
                .publicId("u-1")
                .username("alice")
                .email("alice@example.com")
                .build();
        ReflectionTestUtils.setField(u, "usernameChangedAt", lastChangedAt);
        // prePersist would normally set publicId/createdAt — we set explicit values above
        return u;
    }

    private ChangeUsernameRequest req(String newUsername) {
        ChangeUsernameRequest r = new ChangeUsernameRequest();
        r.setNewUsername(newUsername);
        return r;
    }

    @Test
    void firstUsernameChangeIsAllowedWithoutCooldown() {
        // usernameChangedAt == null ⇒ user has never changed their name; first change is free
        User u = userWithLastChangedAt(null);
        when(userRepository.findByPublicId("u-1")).thenReturn(Optional.of(u));
        when(userRepository.existsByUsername("aliceTwo")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthService svc = buildService(1209600); // 14 days
        String result = svc.changeUsername("u-1", req("aliceTwo"));

        assertThat(result).isEqualTo("aliceTwo");
        assertThat(u.getUsername()).isEqualTo("aliceTwo");
        assertThat(u.getUsernameChangedAt()).isNotNull(); // timestamp recorded
    }

    @Test
    void changeWithinCooldownIsRejectedWithNextAllowedAt() {
        LocalDateTime now = LocalDateTime.now();
        // Set last change to 3 seconds ago — well within the 10s cooldown
        User u = userWithLastChangedAt(now.minusSeconds(3));
        when(userRepository.findByPublicId("u-1")).thenReturn(Optional.of(u));

        AuthService svc = buildService(10);

        assertThatThrownBy(() -> svc.changeUsername("u-1", req("aliceTwo")))
                .isInstanceOf(UsernameChangeCooldownException.class)
                .satisfies(ex -> {
                    UsernameChangeCooldownException cex = (UsernameChangeCooldownException) ex;
                    // nextAllowedAt should be ~7s in the future (last+10s), allowing for test slop
                    assertThat(cex.getNextAllowedAt())
                            .isAfter(now.minusSeconds(1))
                            .isBefore(now.plusSeconds(15));
                    assertThat(cex.getMessage()).contains("change your username again");
                });

        // Username must NOT have been mutated on rejection
        assertThat(u.getUsername()).isEqualTo("alice");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

    @Test
    void changeAfterCooldownIsAllowed() {
        LocalDateTime now = LocalDateTime.now();
        User u = userWithLastChangedAt(now.minusSeconds(60)); // 60s ago, cooldown=10s ⇒ past
        when(userRepository.findByPublicId("u-1")).thenReturn(Optional.of(u));
        when(userRepository.existsByUsername("aliceTwo")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthService svc = buildService(10);

        String result = svc.changeUsername("u-1", req("aliceTwo"));

        assertThat(result).isEqualTo("aliceTwo");
        assertThat(u.getUsernameChangedAt()).isNotNull();
    }

    @Test
    void takenUsernameRejectionStillSurfacesAsBadRequest() {
        // Cooldown path must not swallow a real "username taken" check.
        // With lastChangedAt null, we should pass the cooldown and hit the
        // uniqueness check.
        User u = userWithLastChangedAt(null);
        when(userRepository.findByPublicId("u-1")).thenReturn(Optional.of(u));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        AuthService svc = buildService(1209600);

        assertThatThrownBy(() -> svc.changeUsername("u-1", req("taken")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void cooldownAccessorReturnsConfiguredSeconds() {
        AuthService svc = buildService(1209600);
        assertThat(svc.getUsernameChangeCooldownSeconds()).isEqualTo(1209600L);

        AuthService custom = buildService(60);
        assertThat(custom.getUsernameChangeCooldownSeconds()).isEqualTo(60L);
    }

    @Test
    void invalidUsernameOnChangeIsRejectedByPolicy() {
        AuthService svc = buildService(1209600);

        // too short
        assertThatThrownBy(() -> svc.changeUsername("u-1", req("ab")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("letters, numbers, underscores, and hyphens");

        // too long
        assertThatThrownBy(() -> svc.changeUsername("u-1", req("a".repeat(31))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("letters, numbers, underscores, and hyphens");

        // invalid characters
        assertThatThrownBy(() -> svc.changeUsername("u-1", req("user name")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("letters, numbers, underscores, and hyphens");

        assertThatThrownBy(() -> svc.changeUsername("u-1", req("user!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("letters, numbers, underscores, and hyphens");
    }

    @Test
    void invalidUsernameOnRegisterIsRejectedByPolicy() {
        AuthService svc = buildService(1209600);
        com.zero1blog.backend.dto.RegisterRequest regReq = new com.zero1blog.backend.dto.RegisterRequest();
        regReq.setEmail("test@example.com");
        regReq.setPassword("password");

        // too short
        regReq.setUsername("ab");
        assertThatThrownBy(() -> svc.register(regReq))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("letters, numbers, underscores, and hyphens");

        // invalid characters
        regReq.setUsername("ab#c");
        assertThatThrownBy(() -> svc.register(regReq))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("letters, numbers, underscores, and hyphens");
    }

    @Test
    void refreshTokenWithValidRefreshTokenAndNoAccessTokenSucceeds() {
        AuthService svc = buildService(1209600);
        User u = userWithLastChangedAt(null);
        com.zero1blog.backend.model.RefreshToken rt = new com.zero1blog.backend.model.RefreshToken();
        rt.setToken("valid-rt");
        rt.setUser(u);

        when(refreshTokenService.findByToken("valid-rt")).thenReturn(rt);
        when(jwtService.generateToken(u.getPublicId(), u.getRole().name())).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(u)).thenReturn(rt);

        com.zero1blog.backend.dto.AuthResponse res = svc.refreshToken("valid-rt", null);

        assertThat(res.getToken()).isEqualTo("new-access-token");
    }

    @Test
    void refreshTokenWithExpiredAccessTokenSucceeds() {
        AuthService svc = buildService(1209600);
        User u = userWithLastChangedAt(null);
        com.zero1blog.backend.model.RefreshToken rt = new com.zero1blog.backend.model.RefreshToken();
        rt.setToken("valid-rt");
        rt.setUser(u);

        io.jsonwebtoken.Claims mockClaims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(mockClaims.getSubject()).thenReturn(u.getPublicId());

        when(refreshTokenService.findByToken("valid-rt")).thenReturn(rt);
        when(jwtService.parseClaims("expired-token")).thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, mockClaims, "Token expired"));
        when(jwtService.generateToken(u.getPublicId(), u.getRole().name())).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(u)).thenReturn(rt);

        com.zero1blog.backend.dto.AuthResponse res = svc.refreshToken("valid-rt", "expired-token");

        assertThat(res.getToken()).isEqualTo("new-access-token");
    }

    @Test
    void refreshTokenWithNonExpiredAccessTokenThrowsException() {
        AuthService svc = buildService(1209600);
        User u = userWithLastChangedAt(null);
        com.zero1blog.backend.model.RefreshToken rt = new com.zero1blog.backend.model.RefreshToken();
        rt.setToken("valid-rt");
        rt.setUser(u);

        io.jsonwebtoken.Claims mockClaims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);

        when(refreshTokenService.findByToken("valid-rt")).thenReturn(rt);
        when(jwtService.parseClaims("valid-token")).thenReturn(mockClaims);

        assertThatThrownBy(() -> svc.refreshToken("valid-rt", "valid-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Access token is not expired");
    }

    @Test
    void refreshTokenWithMismatchingUserAccessTokenThrowsException() {
        AuthService svc = buildService(1209600);
        User u = userWithLastChangedAt(null);
        com.zero1blog.backend.model.RefreshToken rt = new com.zero1blog.backend.model.RefreshToken();
        rt.setToken("valid-rt");
        rt.setUser(u);

        io.jsonwebtoken.Claims mockClaims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(mockClaims.getSubject()).thenReturn("different-user");

        when(refreshTokenService.findByToken("valid-rt")).thenReturn(rt);
        when(jwtService.parseClaims("expired-token")).thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, mockClaims, "Token expired"));

        assertThatThrownBy(() -> svc.refreshToken("valid-rt", "expired-token"))
                .isInstanceOf(com.zero1blog.backend.exception.UnauthorizedActionException.class)
                .hasMessageContaining("Invalid session mapping");
    }

    @Test
    void refreshTokenWithMalformedAccessTokenThrowsException() {
        AuthService svc = buildService(1209600);
        User u = userWithLastChangedAt(null);
        com.zero1blog.backend.model.RefreshToken rt = new com.zero1blog.backend.model.RefreshToken();
        rt.setToken("valid-rt");
        rt.setUser(u);

        when(refreshTokenService.findByToken("valid-rt")).thenReturn(rt);
        when(jwtService.parseClaims("malformed-token")).thenThrow(new io.jsonwebtoken.MalformedJwtException("Malformed token"));

        assertThatThrownBy(() -> svc.refreshToken("valid-rt", "malformed-token"))
                .isInstanceOf(com.zero1blog.backend.exception.UnauthorizedActionException.class)
                .hasMessageContaining("Malformed token");
    }
}
