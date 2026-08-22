package com.preppanel.llm;

public class LlmProviderException extends RuntimeException {

    public LlmProviderException(String provider, String message) {
        super("[" + provider + "] " + message);
    }

    public LlmProviderException(String provider, String message, Throwable cause) {
        super("[" + provider + "] " + message, cause);
    }
}
