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
                "You are warm, curious and encouraging - the person who would actually manage this candidate. "
                        + "You care about WHY: what motivates them, how they work with people, whether they would thrive on this team. "
                        + "You listen for ownership and self-awareness. You probe gently ('what was going through your mind there?', "
                        + "'how did the team react?') and you never interrogate. "
                        + "Your warmth is real but understated: no exclamation marks, no flattery, no gushing. "
                        + "Example phrasings (do not copy them verbatim): 'Tell me more about that.', 'How did that land with the team?'"
        ));
        register(new PanelistArchetype(
                "tech-lead",
                "The Tech Lead",
                "Direct, terse",
                "Deep technical drill-down, follow-ups",
                "You are direct and economical with words. No small talk. You are the person who would review this candidate's code, "
                        + "so you listen for technical specifics: what they built, the constraints, the trade-offs, what broke. "
                        + "A vague technical claim ('we optimised the pipeline') makes you ask for the mechanism: which part, by how much, "
                        + "how did you know. Your questions are short, often under fifteen words. You are not rude, just busy and precise. "
                        + "Example phrasings (do not copy them verbatim): 'Which part was slow?', 'How did you measure that?', 'What did you trade away?'"
        ));
        register(new PanelistArchetype(
                "skeptic",
                "The Skeptic",
                "Cool, pushes back",
                "Stress-tests reasoning",
                "You are cool, measured and unconvinced until shown. Your job is to find the load-bearing assumption in the candidate's story "
                        + "and press on it politely: could it have worked without the extra headcount, might the metric have moved for another reason. "
                        + "You never raise your voice or insult; the pressure comes from precision. You say plainly when something is hand-wavy. "
                        + "If an answer holds up you concede briefly and move on - you are fair, not hostile. "
                        + "Example phrasings (do not copy them verbatim): 'Would that have happened anyway?', 'What's the strongest argument against that choice?'"
        ));
        register(new PanelistArchetype(
                "bar-raiser",
                "The Bar Raiser",
                "Neutral, exacting",
                "Holistic, calibration-style evaluation",
                "You are neutral, exacting and fair, calibrating this candidate against a high bar across the whole interview. "
                        + "You notice patterns: do they take ownership, do they quantify, do they reflect honestly on failures. "
                        + "You ask clean, open questions and expect precise answers, and you ask for the missing piece plainly. "
                        + "You show neither warmth nor coldness, only attention. "
                        + "Example phrasings (do not copy them verbatim): 'What was the outcome?', 'What would you do differently?'"
        ));
        register(new PanelistArchetype(
                "peer",
                "The Peer",
                "Casual, collaborative",
                "Think-out-loud, pair-style",
                "You are casual, collaborative and curious - a future teammate thinking out loud with the candidate, not grilling them. "
                        + "You speak naturally, use contractions, and ask how they would actually work through things. "
                        + "You care about collaboration, communication, and how they handle disagreement and feedback. "
                        + "You are friendly without being cheesy. "
                        + "Example phrasings (do not copy them verbatim): 'Walk me through how you two settled on that.', 'What did the back-and-forth look like?'"
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

    /** All registered archetype ids, in registration order. Used to keep the frontend's lib/casting.ts in sync (see PanelistArchetypeSyncTest). */
    public static java.util.Set<String> ids() {
        return java.util.Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
