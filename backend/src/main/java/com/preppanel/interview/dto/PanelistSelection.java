package com.preppanel.interview.dto;

public record PanelistSelection(
        String archetypeId,
        String name // optional custom name - falls back to archetype display name if blank
) {
}
