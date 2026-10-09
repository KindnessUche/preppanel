package com.preppanel.profile.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePortfolioRequest(
        @Pattern(regexp = "^$|^[A-Za-z0-9-]{1,39}$", message = "Not a valid GitHub username")
        String githubUsername, // optional - null/blank clears it

        @Size(max = 10, message = "At most 10 portfolio URLs")
        List<@Pattern(regexp = "^https?://.+", message = "Portfolio URLs must be http(s) links") String> portfolioUrls
) {
}
