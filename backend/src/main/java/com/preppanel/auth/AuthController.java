package com.preppanel.auth;

import com.preppanel.auth.AuthService.TokenIssuance;
import com.preppanel.auth.dto.AuthResponse;
import com.preppanel.auth.dto.LoginRequest;
import com.preppanel.auth.dto.RegisterRequest;
import com.preppanel.security.AppUserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "preppanel_refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;

    /** false in local dev over http, should be true in any real deployment (https). */
    @Value("${app.cookie.secure:false}")
    private boolean secureCookies;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenIssuance issuance = authService.register(request);
        return withRefreshCookie(HttpStatus.CREATED, issuance);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenIssuance issuance = authService.login(request);
        return withRefreshCookie(HttpStatus.OK, issuance);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String rawRefreshToken = readRefreshCookie(request);
        TokenIssuance issuance = authService.refresh(rawRefreshToken);
        return withRefreshCookie(HttpStatus.OK, issuance);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String rawRefreshToken = readRefreshCookie(request);
        authService.logout(rawRefreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
    }

    /** Requires a valid access token. Revokes every refresh token for the caller. */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        authService.logoutAllSessions(principal.getId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, TokenIssuance issuance) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, issuance.rawRefreshToken())
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(java.time.Duration.ofDays(30))
                .build();

        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(issuance.response());
    }

    private ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
