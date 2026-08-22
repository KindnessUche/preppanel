package com.preppanel.llm;

import com.preppanel.panel.PanelistArchetype;

/** A panelist as cast into a specific session: their resolved display name (custom or default) plus the underlying archetype persona. */
public record PanelistRosterEntry(String name, PanelistArchetype archetype) {
}
