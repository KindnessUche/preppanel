package com.preppanel.security;

import com.preppanel.llm.LlmCredentials;
import com.preppanel.llm.LlmProviders;
import com.preppanel.llm.RequestCredentials;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the user's API keys from request headers into RequestCredentials for the duration of ONE
 * request, then clears them. The keys live in the browser (localStorage); the backend only ever
 * holds them in memory while serving the call and never stores or logs them.
 *
 *   X-LLM-Provider   anthropic | openai | gemini | xai | groq
 *   X-LLM-Key        the provider API key
 *   X-LLM-Model      optional; falls back to the provider's default model
 *   X-AssemblyAI-Key the user's AssemblyAI key (speech-to-text)
 *
 * Send these over HTTPS when deployed - over plain http they're readable on the network.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class CredentialHeaderFilter extends OncePerRequestFilter {

    static final int MAX_HEADER_LENGTH = 512;

    private final RequestCredentials requestCredentials;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            requestCredentials.set(readLlm(request), clean(request.getHeader("X-AssemblyAI-Key")));
            filterChain.doFilter(request, response);
        } finally {
            requestCredentials.clear(); // never let a key leak to the next request on this thread
        }
    }

    private LlmCredentials readLlm(HttpServletRequest request) {
        String provider = clean(request.getHeader("X-LLM-Provider"));
        String key = clean(request.getHeader("X-LLM-Key"));
        if (provider == null || key == null) {
            return null;
        }
        String model = clean(request.getHeader("X-LLM-Model"));
        if (model == null) {
            model = LlmProviders.find(provider).map(LlmProviders.Spec::defaultModel).orElse(null);
        }
        // An unknown provider is kept as-is; LlmService turns it into a clear 400 for the user.
        return new LlmCredentials(provider.toLowerCase(), key, model);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.length() > MAX_HEADER_LENGTH ? null : trimmed;
    }
}
