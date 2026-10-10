package com.preppanel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Protects two things that matter for a free-tier-LLM-dependent app:
 *  - /api/auth/login against brute force (rate-limited per client IP)
 *  - every endpoint that triggers an LLM call against quota exhaustion
 *    (rate-limited per authenticated user, since it's cheap for one buggy
 *    frontend retry loop or one bad actor to burn the whole day's Groq quota)
 *
 * This runs before JwtAuthFilter's authentication is fully relevant for the
 * login case (anonymous, keyed by IP) and after it for the LLM case
 * (authenticated, keyed by user id) - so it's registered to run just after
 * JwtAuthFilter in SecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
    private static final int LOGIN_MAX_HITS = 10;

    private static final Duration LLM_WINDOW = Duration.ofMinutes(1);
    private static final int LLM_MAX_HITS = 8;

    /** One token per question in hands-free mode; separate from the LLM bucket so voice never eats answer quota. */
    private static final int TOKEN_MAX_HITS = 20;

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && path.equals("/api/auth/login")) {
            if (!rateLimiter.allow("login:" + clientIp(request), LOGIN_MAX_HITS, LOGIN_WINDOW)) {
                rejectTooManyRequests(response, "Too many login attempts. Please wait a minute and try again.");
                return;
            }
        } else if ("GET".equals(method) && path.equals("/api/transcribe/token")) {
            String userId = currentUserId();
            if (userId != null && !rateLimiter.allow("aai-token:" + userId, TOKEN_MAX_HITS, LLM_WINDOW)) {
                rejectTooManyRequests(response, "You're doing that too quickly - please slow down a bit.");
                return;
            }
        } else if ("POST".equals(method) && isLlmCallingPath(path)) {
            String userId = currentUserId();
            if (userId != null && !rateLimiter.allow("llm:" + userId, LLM_MAX_HITS, LLM_WINDOW)) {
                rejectTooManyRequests(response, "You're doing that too quickly - please slow down a bit.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** POST /api/interviews, POST .../answers, POST .../complete all trigger at least one LLM call. */
    private boolean isLlmCallingPath(String path) {
        if (path.equals("/api/transcribe") || path.equals("/api/llm/test")) return true; // third-party calls
        if (!path.startsWith("/api/interviews")) return false;
        return path.equals("/api/interviews")
                || path.endsWith("/answers")
                || path.endsWith("/complete")
                || path.endsWith("/score");
    }

    private String currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return principal.getId().toString();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void rejectTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "message", message
        ));
    }
}
