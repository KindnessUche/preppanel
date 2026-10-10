package com.preppanel.llm;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/** Shared HTTP bits for the provider clients. */
final class LlmHttp {

    /** Reasoning models can be slow; this still bounds how long a request thread can hang. */
    static final Duration TIMEOUT = Duration.ofSeconds(60);

    private LlmHttp() {
    }

    /**
     * Turns an HTTP failure into either a user-fixable LlmConfigException (bad key / unknown model)
     * or a transient LlmProviderException (rate limit, outage), which callers may fall back from.
     * Provider error bodies are NOT copied into messages - some echo parts of the key.
     */
    static RuntimeException translate(String provider, Throwable error) {
        if (error instanceof WebClientResponseException http) {
            int status = http.getStatusCode().value();
            String body = http.getResponseBodyAsString().toLowerCase();

            boolean keyProblem = status == 401 || status == 403
                    || (status == 400 && (body.contains("api key") || body.contains("api_key") || body.contains("authentication")));
            if (keyProblem) {
                return new LlmConfigException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Your " + provider + " API key was rejected. Check it in Account → API keys.");
            }
            if (status == 404 || (status == 400 && body.contains("model"))) {
                return new LlmConfigException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "The model name isn't available for your " + provider + " account. Check the model in Account → API keys.");
            }
            if (status == 429) {
                return new LlmProviderException(provider, "Rate limited or out of quota (HTTP 429)");
            }
            return new LlmProviderException(provider, "Request failed with HTTP " + status);
        }
        if (error instanceof LlmProviderException || error instanceof LlmConfigException) {
            return (RuntimeException) error;
        }
        return new LlmProviderException(provider, "Request failed: " + error.getClass().getSimpleName());
    }
}
