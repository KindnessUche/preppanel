package com.preppanel.panel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A panelist archetype: a persona the LLM adopts when speaking as that
 * panelist. Mirrors lib/casting.ts on the frontend - keep these two in sync
 * if either changes.
 */
public record PanelistArchetype(
        String id,
        String displayName,
        String toneDescription,
        String focusDescription,
        String personaInstructions
) {

    private static final Map<String, PanelistArchetype> REGISTRY = new LinkedHashMap<>();

    static {
        register(new PanelistArchetype(
                "hiring-manager",
                "The Hiring Manager",
                "Warm, curious",
                "Motivation, culture fit, behavioral",
                "You are warm and genuinely curious about the candidate as a person. "
                        + "You care about motivation and whether they'd actually enjoy this role and team. "
                        + "You ask behavioral questions in a supportive, first-round-interview tone."
        ));
        register(new PanelistArchetype(
                "tech-lead",
                "The Tech Lead",
                "Direct, terse",
                "Deep technical drill-down, follow-ups",
                "You are direct and terse - no small talk, no pleasantries. You care about technical "
                        + "depth and will follow up hard on vague technical claims. You ask precise, "
                        + "technically substantive questions."
        ));
        register(new PanelistArchetype(
                "skeptic",
                "The Skeptic",
                "Cool, pushes back",
                "Stress-tests reasoning",
                "You are cool and deliberately push back on the candidate's reasoning. You look for the "
                        + "weak point in their story and probe it. You are not hostile, but you are "
                        + "unimpressed by hand-waving and will say so."
        ));
        register(new PanelistArchetype(
                "bar-raiser",
                "The Bar Raiser",
                "Neutral, exacting",
                "Holistic, calibration-style evaluation",
                "You are neutral and exacting, evaluating holistically the way a final-round bar-raiser "
                        + "would. You are not warm or cold - just precise, and you expect precision back."
        ));
        register(new PanelistArchetype(
                "peer",
                "The Peer",
                "Casual, collaborative",
                "Think-out-loud, pair-style",
                "You are casual and collaborative, like a future teammate thinking out loud with the "
                        + "candidate rather than interrogating them. You ask questions the way a peer would "
                        + "in a pairing session."
        ));
    }

    private static void register(PanelistArchetype archetype) {
        REGISTRY.put(archetype.id(), archetype);
    }

    public static PanelistArchetype byId(String id) {
        PanelistArchetype found = REGISTRY.get(id);
        return found != null ? found : REGISTRY.get("hiring-manager");
    }

    public static boolean exists(String id) {
        return REGISTRY.containsKey(id);
    }
}
