package com.preppanel.llm.dto;

/** What kind of move the panel is making on a given turn. Stored in questions.turn_type as the lower-case name. */
public enum TurnType {
    OPENING,
    NEW_TOPIC,
    PROBE,
    CHALLENGE,
    REDIRECT, // candidate dodged, refused, or drifted: restate the question, don't move on
    WRAP_UP;

    public String db() {
        return name().toLowerCase();
    }

    /** Unknown/null (e.g. rows from before the interview-plan migration) are treated as ordinary topic questions. */
    public static TurnType fromDb(String value) {
        if (value == null) return NEW_TOPIC;
        for (TurnType t : values()) {
            if (t.db().equals(value.trim().toLowerCase())) return t;
        }
        return NEW_TOPIC;
    }

    public boolean isFollowUp() {
        return this == PROBE || this == CHALLENGE || this == REDIRECT;
    }
}
