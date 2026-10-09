package com.preppanel.profile.dto;

import java.time.Instant;
import java.util.List;

public record ProfileResponse(
        boolean hasResume,
        String resumeFilename,
        List<String> parsedSkills,
        String githubUsername,
        List<String> portfolioUrls,
        Instant updatedAt
) {
}
