package com.preppanel.llm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Providers a user can pick in Settings, with default model names (user-overridable in the UI). */
public final class LlmProviders {

    public record Spec(String id, String label, String baseUrl, String defaultModel) {
    }

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        add(new Spec("anthropic", "Claude (Anthropic)", "https://api.anthropic.com", "claude-sonnet-5-5"));
        add(new Spec("openai", "ChatGPT (OpenAI)", "https://api.openai.com/v1", "gpt-5.5"));
        add(new Spec("gemini", "Gemini (Google)", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.1-flash-lite"));
        add(new Spec("xai", "Grok (xAI)", "https://api.x.ai/v1", "grok-4.3"));
        add(new Spec("groq", "Groq (free tier)", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"));
    }

    private LlmProviders() {
    }

    private static void add(Spec spec) {
        SPECS.put(spec.id(), spec);
    }

    public static Optional<Spec> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(SPECS.get(id.toLowerCase().trim()));
    }
}
