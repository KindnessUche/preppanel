package com.preppanel.interview;

import com.preppanel.interview.dto.*;
import com.preppanel.security.AppUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(Authentication auth,
                                                           @Valid @RequestBody CreateSessionRequest request) {
        UUID userId = userId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(interviewService.createSession(userId, request));
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<SessionResponse> submitAnswer(Authentication auth,
                                                          @PathVariable("id") UUID sessionId,
                                                          @Valid @RequestBody SubmitAnswerRequest request) {
        return ResponseEntity.ok(interviewService.submitAnswer(userId(auth), sessionId, request));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<SessionResponse> complete(Authentication auth, @PathVariable("id") UUID sessionId) {
        return ResponseEntity.ok(interviewService.completeSession(userId(auth), sessionId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDetailResponse> getSession(Authentication auth, @PathVariable("id") UUID sessionId) {
        return ResponseEntity.ok(interviewService.getSessionDetail(userId(auth), sessionId));
    }

    private UUID userId(Authentication auth) {
        return ((AppUserPrincipal) auth.getPrincipal()).getId();
    }
}
