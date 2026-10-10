package com.preppanel.llm;

import com.preppanel.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmServiceTest {

    /** Records every call and either returns "ok" or throws. */
    static class FakeClient implements LlmClientService {
        final Set<String> providers;
        final List<LlmCredentials> calls = new ArrayList<>();
        RuntimeException failWith;

        FakeClient(String... providers) {
            this.providers = Set.of(providers);
        }

        @Override
        public Set<String> providers() {
            return providers;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, LlmCredentials credentials) {
            calls.add(credentials);
            if (failWith != null) throw failWith;
            return "ok";
        }
    }

    private final RequestCredentials requestCredentials = new RequestCredentials();

    @AfterEach
    void clear() {
        requestCredentials.clear();
    }

    private LlmProperties noServerKeys() {
        return new LlmProperties("groq", "gemini",
                new LlmProperties.Groq("", "x", "m"), new LlmProperties.Gemini("", "x", "m"));
    }

    @Test
    void usesTheUsersOwnKeyAndModel_whenSentWithTheRequest() {
        FakeClient anthropic = new FakeClient("anthropic");
        LlmService service = new LlmService(List.of(anthropic), noServerKeys(), requestCredentials);
        requestCredentials.set(new LlmCredentials("anthropic", "sk-ant-123", "claude-sonnet-5-5"), null);

        assertThat(service.ping()).isEqualTo("ok");

        assertThat(anthropic.calls).hasSize(1);
        assertThat(anthropic.calls.get(0).apiKey()).isEqualTo("sk-ant-123");
        assertThat(anthropic.calls.get(0).model()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void noUserKeyAndNoServerKeys_givesAClearSetupError() {
        LlmService service = new LlmService(List.of(new FakeClient("groq")), noServerKeys(), requestCredentials);

        assertThatThrownBy(service::ping)
                .isInstanceOf(LlmConfigException.class)
                .hasMessageContaining("API keys");
    }

    @Test
    void unknownProvider_isRejectedWith400() {
        LlmService service = new LlmService(List.of(new FakeClient("openai")), noServerKeys(), requestCredentials);
        requestCredentials.set(new LlmCredentials("skynet", "k", "m"), null);

        assertThatThrownBy(service::ping)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown AI provider");
    }

    @Test
    void userKeyFailure_isNotRetriedOnAnotherProvider() {
        FakeClient openai = new FakeClient("openai");
        openai.failWith = new LlmConfigException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "rejected");
        FakeClient groq = new FakeClient("groq");
        LlmProperties serverKeys = new LlmProperties("groq", "gemini",
                new LlmProperties.Groq("server-groq-key", "x", "m"), new LlmProperties.Gemini("", "x", "m"));
        LlmService service = new LlmService(List.of(openai, groq), serverKeys, requestCredentials);
        requestCredentials.set(new LlmCredentials("openai", "bad", "gpt-5.5"), null);

        assertThatThrownBy(service::ping).isInstanceOf(LlmConfigException.class);
        assertThat(groq.calls).isEmpty(); // the user's choice is respected, no silent switch
    }

    @Test
    void serverDefaults_fallBackFromPrimaryToSecondary() {
        FakeClient groq = new FakeClient("groq");
        groq.failWith = new LlmProviderException("groq", "down");
        FakeClient gemini = new FakeClient("gemini");
        LlmProperties serverKeys = new LlmProperties("groq", "gemini",
                new LlmProperties.Groq("g1", "x", "m1"), new LlmProperties.Gemini("g2", "x", "m2"));
        LlmService service = new LlmService(List.of(groq, gemini), serverKeys, requestCredentials);

        assertThat(service.ping()).isEqualTo("ok");
        assertThat(gemini.calls).hasSize(1);
    }

    @Test
    void credentialsToString_neverPrintsTheKey() {
        assertThat(new LlmCredentials("openai", "sk-secret-value", "gpt-5.5").toString())
                .doesNotContain("sk-secret-value");
    }

    @Test
    void requestCredentials_areClearedAfterUse() {
        requestCredentials.set(new LlmCredentials("openai", "k", "m"), "aai");
        requestCredentials.clear();
        assertThat(requestCredentials.llm()).isEmpty();
        assertThat(requestCredentials.assemblyAiKey()).isEmpty();
    }
}
