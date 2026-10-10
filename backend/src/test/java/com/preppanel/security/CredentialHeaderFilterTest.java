package com.preppanel.security;

import com.preppanel.llm.LlmCredentials;
import com.preppanel.llm.RequestCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialHeaderFilterTest {

    private final RequestCredentials credentials = new RequestCredentials();
    private final CredentialHeaderFilter filter = new CredentialHeaderFilter(credentials);

    @Test
    void keysAreAvailableDuringTheRequest_withDefaultModel_andGoneAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/interviews");
        request.addHeader("X-LLM-Provider", "OpenAI");
        request.addHeader("X-LLM-Key", "  sk-test  ");
        request.addHeader("X-AssemblyAI-Key", "aai-test");

        AtomicReference<LlmCredentials> llmSeen = new AtomicReference<>();
        AtomicReference<String> aaiSeen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            llmSeen.set(credentials.llm().orElse(null));
            aaiSeen.set(credentials.assemblyAiKey().orElse(null));
        });

        assertThat(llmSeen.get().provider()).isEqualTo("openai");
        assertThat(llmSeen.get().apiKey()).isEqualTo("sk-test");
        assertThat(llmSeen.get().model()).isEqualTo("gpt-5.5"); // provider default
        assertThat(aaiSeen.get()).isEqualTo("aai-test");

        // Nothing may survive past the request.
        assertThat(credentials.llm()).isEmpty();
        assertThat(credentials.assemblyAiKey()).isEmpty();
    }

    @Test
    void explicitModelHeaderWins() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/interviews");
        request.addHeader("X-LLM-Provider", "anthropic");
        request.addHeader("X-LLM-Key", "k");
        request.addHeader("X-LLM-Model", "claude-haiku-5-5");

        AtomicReference<LlmCredentials> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(credentials.llm().orElse(null)));

        assertThat(seen.get().model()).isEqualTo("claude-haiku-5-5");
    }

    @Test
    void keyWithoutProvider_orNoHeaders_meansNoCredentials() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/interviews");
        request.addHeader("X-LLM-Key", "orphan-key");

        AtomicReference<Boolean> hadLlm = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> hadLlm.set(credentials.llm().isPresent()));

        assertThat(hadLlm.get()).isFalse();
    }

    @Test
    void credentialsAreClearedEvenWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/interviews");
        request.addHeader("X-LLM-Provider", "groq");
        request.addHeader("X-LLM-Key", "k");

        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            // the failure propagates; what matters is the cleanup below
        }
        assertThat(credentials.llm()).isEmpty();
    }
}
