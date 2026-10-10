package com.preppanel.llm;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Holds the API keys the browser sent with the CURRENT request, in memory only, for the
 * duration of that request. CredentialHeaderFilter fills it and always clears it afterwards.
 * Nothing here is written to the database, to logs, or shared between requests.
 */
@Component
public class RequestCredentials {

    private record Held(LlmCredentials llm, String assemblyAiKey) {
    }

    private static final ThreadLocal<Held> HOLDER = new ThreadLocal<>();

    public void set(LlmCredentials llm, String assemblyAiKey) {
        HOLDER.set(new Held(llm, assemblyAiKey));
    }

    public Optional<LlmCredentials> llm() {
        Held held = HOLDER.get();
        return held == null ? Optional.empty() : Optional.ofNullable(held.llm());
    }

    public Optional<String> assemblyAiKey() {
        Held held = HOLDER.get();
        return held == null || held.assemblyAiKey() == null || held.assemblyAiKey().isBlank()
                ? Optional.empty() : Optional.of(held.assemblyAiKey());
    }

    public void clear() {
        HOLDER.remove();
    }
}
