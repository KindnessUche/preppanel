package com.preppanel.llm;

import com.preppanel.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmService llmService;

    /** Settings page "Test connection": makes one tiny call using the key sent in the headers. */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        try {
            llmService.ping();
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (LlmProviderException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The provider didn't respond properly: " + e.getMessage());
        }
    }
}
