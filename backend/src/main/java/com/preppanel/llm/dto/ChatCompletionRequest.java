package com.preppanel.llm.dto;

import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        Double temperature
) {
    public record Message(String role, String content) {
    }
}
