package com.preppanel.llm;

/**
 * Abstraction over LLM providers. Implementations should be pure - given a prompt,
 * return the model's raw text response, or throw on failure so the caller (LlmService)
 * can fall back to the next provider.
 */
public interface LlmClientService {

    /** Short identifier used in logs/errors, e.g. "groq" or "gemini". */
    String providerName();

    /** Sends a single user-role prompt and returns the model's text response. */
    String complete(String systemPrompt, String userPrompt);
}
