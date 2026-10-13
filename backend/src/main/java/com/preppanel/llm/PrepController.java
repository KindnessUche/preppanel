package com.preppanel.llm;

import com.preppanel.profile.ProfileService;
import com.preppanel.security.AppUserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Builds the pre-interview brief. Returns the model's JSON as-is after extracting the object; the UI renders it defensively. */
@RestController
@RequestMapping("/api/prep")
@RequiredArgsConstructor
public class PrepController {

    private final LlmService llmService;
    private final ProfileService profileService;

    public record BriefRequest(
            @NotBlank @Size(max = 120) String role,
            @Size(max = 40) String level,
            @Size(max = 60) String interviewType,
            @Size(max = 120) String company,
            @Size(max = 12000) String jobDescription) {
    }

    @PostMapping(value = "/brief", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> brief(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody @jakarta.validation.Valid BriefRequest req) {
        String summary = profileService.getCandidateSummaryForPrompt(principal.getId());
        String raw = llmService.generatePrepBrief(req.role(), req.level(), req.interviewType(), req.company(), req.jobDescription(), summary);
        return ResponseEntity.ok(LlmJson.extractObject(raw));
    }
}
