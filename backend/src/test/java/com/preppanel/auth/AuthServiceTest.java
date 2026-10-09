package com.preppanel.auth;

import com.preppanel.auth.dto.LoginRequest;
import com.preppanel.auth.dto.RegisterRequest;
import com.preppanel.exception.ApiException;
import com.preppanel.security.JwtService;
import com.preppanel.token.RefreshToken;
import com.preppanel.token.RefreshTokenRepository;
import com.preppanel.user.User;
import com.preppanel.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService);
    }

    private User existingUser() {
        return User.builder()
                .id(userId)
                .email("candidate@example.com")
                .passwordHash("hashed")
                .build();
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("candidate@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("candidate@example.com", "password123")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_hashesPasswordAndIssuesTokenPair() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(userId);
            return u;
        });
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(jwtService.refreshTokenTtlDays()).thenReturn(30L);
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        AuthService.TokenIssuance issuance =
                authService.register(new RegisterRequest("candidate@example.com", "password123"));

        assertThat(issuance.response().accessToken()).isEqualTo("access-token");
        assertThat(issuance.response().email()).isEqualTo("candidate@example.com");
        assertThat(issuance.rawRefreshToken()).isEqualTo("raw-refresh-token");

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-pw");

        ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(savedToken.capture());
        assertThat(savedToken.getValue().getTokenHash()).isEqualTo("hashed-refresh-token");
        assertThat(savedToken.getValue().isRevoked()).isFalse();
    }

    @Test
    void login_rejectsWrongPassword() {
        when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("candidate@example.com", "wrong")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("nobody@example.com", "whatever1")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void refresh_rotatesToken_andRejectsReuseOfRevokedToken() {
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hashed-old")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(jwtService.hashToken("raw-old")).thenReturn("hashed-old");
        when(refreshTokenRepository.findByTokenHash("hashed-old")).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser()));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("new-access");
        when(jwtService.generateOpaqueRefreshToken()).thenReturn("raw-new");
        when(jwtService.hashToken("raw-new")).thenReturn("hashed-new");
        when(jwtService.refreshTokenTtlDays()).thenReturn(30L);
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        AuthService.TokenIssuance issuance = authService.refresh("raw-old");

        assertThat(issuance.rawRefreshToken()).isEqualTo("raw-new");
        assertThat(stored.isRevoked()).isTrue(); // old token rotated out

        // Simulate the old (now-revoked) token being replayed - must be rejected.
        when(refreshTokenRepository.findByTokenHash("hashed-old")).thenReturn(Optional.of(stored));
        assertThatThrownBy(() -> authService.refresh("raw-old"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired or revoked");
    }

    @Test
    void refresh_rejectsExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hashed-expired")
                .expiresAt(Instant.now().minusSeconds(60))
                .revoked(false)
                .build();

        when(jwtService.hashToken("raw-expired")).thenReturn("hashed-expired");
        when(refreshTokenRepository.findByTokenHash("hashed-expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-expired"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired or revoked");
    }

    @Test
    void refresh_rejectsMissingToken() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> authService.refresh("  "))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void logoutAllSessions_revokesEveryTokenForUser() {
        authService.logoutAllSessions(userId);
        verify(refreshTokenRepository).revokeAllForUser(userId);
    }

    @Test
    void logout_withNoToken_isANoOp() {
        authService.logout(null);
        verifyNoInteractions(refreshTokenRepository);
    }
}
