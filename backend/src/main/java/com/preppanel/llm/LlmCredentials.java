package com.preppanel.llm;

/**
 * A user's provider + key + model for ONE request. Never persisted and never logged:
 * toString is overridden because a record's default toString would print the key.
 */
public record LlmCredentials(String provider, String apiKey, String model) {

    @Override
    public String toString() {
        return "LlmCredentials[provider=" + provider + ", model=" + model + ", apiKey=****]";
    }
}
