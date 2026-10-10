package com.preppanel.llm;

import com.preppanel.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * "Fix your settings" problems (no key, key rejected, model not found). It is an ApiException so
 * it reaches the user instead of being swallowed by the question-bank fallback. Deliberately never
 * 401/403: the frontend treats 401 as "session expired" and would try to refresh the login.
 */
public class LlmConfigException extends ApiException {

    public LlmConfigException(HttpStatus status, String message) {
        super(status, message);
    }
}
