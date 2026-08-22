package com.preppanel.interview;

import com.preppanel.interview.dto.SessionReportResponse;
import com.preppanel.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class ScoringController {

    private final ScoringService scoringService;

    /**
     * Scores every answered-but-unscored question in the session via the LLM judge.
     * Idempotent - safe to call again if it partially failed or new answers were added.
     */
    @PostMapping("/{id}/score")
    public ResponseEntity<SessionReportResponse> score(Authentication auth, @PathVariable("id") UUID sessionId) {
        return ResponseEntity.ok(scoringService.scoreSession(userId(auth), sessionId));
    }

    /** Returns the current report without triggering any new scoring. */
    @GetMapping("/{id}/report")
    public ResponseEntity<SessionReportResponse> report(Authentication auth, @PathVariable("id") UUID sessionId) {
        return ResponseEntity.ok(scoringService.getReport(userId(auth), sessionId));
    }

    private UUID userId(Authentication auth) {
        return ((AppUserPrincipal) auth.getPrincipal()).getId();
    }
}
