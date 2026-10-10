package com.preppanel.llm;

import java.util.Set;

/**
 * Abstraction over LLM vendors. Implementations are stateless: the key and model come in with each
 * call (from the user's Settings), never from stored configuration.
 */
public interface LlmClientService {

    /** Provider ids this client handles (see LlmProviders), e.g. {"openai","xai","groq"}. */
    Set<String> providers();

    /** Sends one system+user prompt and returns the model's text. Throws on failure. */
    String complete(String systemPrompt, String userPrompt, LlmCredentials credentials);
}
