package com.preppanel.llm;

import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.InterviewPlan;
import com.preppanel.llm.dto.TurnPlan;
import com.preppanel.llm.dto.TurnType;

import java.util.List;

/**
 * All prompt text for the planned-interview engine, kept apart from the HTTP/provider plumbing in
 * {@link LlmService} so the wording can be read, tested and tuned in one place.
 */
final class InterviewPrompts {

    private InterviewPrompts() {
    }

    record Prompt(String system, String user) {
    }

    // ------------------------------------------------------------------ plan

    static Prompt plan(String role, String level, String candidateSummary, List<PanelistRosterEntry> roster, int topicCount) {
        String system = """
                You design the structure of a realistic mock job interview. You do NOT write the questions yet -
                you choose the topics the panel will cover.

                Rules:
                - Choose exactly %d topics, ordered so the interview builds naturally: accessible topics first,
                  harder or more probing ones later.
                - Each topic is a competency a real interviewer for this role would want evidence of, drawn out
                  through a real past example (never a hypothetical).
                - Make topics specific to the role, not generic. "Owning a service through an outage" beats "Teamwork".
                - Give each topic a lens, exactly one of: technical, collaboration, ownership, judgment, growth,
                  motivation, leadership.
                - Use at least three different lenses. If the role is technical, at least one topic must have the
                  lens "technical" (a past system, decision or bug the candidate owned).
                - If the candidate background mentions concrete projects, employers or skills, at least one topic must
                  have a resumeHook: a specific thing from their background to dig into (for example "the payments
                  migration at Acme"). Otherwise resumeHook is null. Never invent facts about the candidate.
                - "focus" is one sentence describing what a strong example would show.

                Respond with ONLY this JSON, no markdown fences, no commentary:
                {"topics":[{"label":"...","lens":"...","focus":"...","resumeHook":null}]}
                """.formatted(topicCount);

        StringBuilder user = new StringBuilder();
        user.append("Role: ").append(role).append("\n");
        user.append("Target level: ").append(level == null || level.isBlank() ? "not specified" : level).append("\n\n");
        user.append("Candidate background:\n").append(background(candidateSummary)).append("\n\n");
        user.append("The panel (their areas of interest should be covered):\n");
        for (PanelistRosterEntry p : roster) {
            user.append("- ").append(p.name()).append(": ").append(p.archetype().focusDescription()).append("\n");
        }
        user.append("\nDesign the topics now.");
        return new Prompt(system, user.toString());
    }

    // ------------------------------------------------------------------ assessor

    static Prompt assess(String role, String level, InterviewPlan.Topic topic, List<TranscriptTurn> topicTurns, String candidateSummary) {
        String system = """
                You are a silent interview assessor. You watch a mock interview and, after each candidate answer,
                privately analyse it so the panel can decide what to do next. The candidate never sees this.

                Identify the SINGLE most important gap in the candidate's latest answer, using exactly one of:
                - "specifics": generic or abstract; no concrete situation, names, timeline or numbers.
                - "ownership": you can't tell what the candidate personally did (all "we", or the team's work).
                - "result": no outcome, impact, or what changed afterwards.
                - "technical_depth": a technical claim with no mechanism, constraint or trade-off behind it.
                - "reasoning": a decision with no rationale and no alternatives considered.
                - "none": the answer is substantive enough to move on.

                Calibration: %s
                Be fair. Do not demand numbers where numbers would be unnatural. If an earlier follow-up on this topic
                already filled a gap, do not report it again. Answers may be voice transcripts with filler words and
                no punctuation - ignore that completely.

                Also report:
                - "standout": the single most concrete or notable thing they said, closely paraphrased in under 20
                  words, so a panelist can react to it accurately. null if nothing stands out.
                - "claimToChallenge": a specific claim a sceptical interviewer would want to stress-test (a large
                  number, a sweeping success claim, a questionable decision), in under 25 words. null if none.
                - "answeredQuestion": false if they dodged, went off-topic, or said they have no example.
                - "rambling": true if the answer is long and unfocused.
                - "gapDetail": if gap is not "none", one sentence naming exactly what is missing, for example
                  "They never said what the outcome was." Otherwise null.

                Respond with ONLY this JSON, no markdown fences, no commentary:
                {"standout":null,"gap":"none","gapDetail":null,"claimToChallenge":null,"answeredQuestion":true,"rambling":false}
                """.formatted(calibration(level));

        StringBuilder user = new StringBuilder();
        user.append("Role: ").append(role).append("\n");
        user.append("Topic: ").append(topic.label()).append(" (").append(topic.focus()).append(")\n\n");
        user.append("Candidate background (for context only):\n").append(background(candidateSummary)).append("\n\n");
        user.append("Conversation on this topic so far:\n");
        for (TranscriptTurn t : topicTurns) {
            user.append(t.panelistName()).append(": ").append(t.questionText()).append("\n");
            user.append("Candidate: ").append(t.answerText()).append("\n\n");
        }
        user.append("Analyse the candidate's LAST answer now.");
        return new Prompt(system, user.toString());
    }

    private static String calibration(String level) {
        String l = level == null ? "" : level.toLowerCase();
        return switch (l) {
            case "junior" -> "Junior candidate: accept coursework, internships and small projects as examples; expect modest scope.";
            case "senior", "staff" -> "Senior candidate: expect scope, trade-offs, influence beyond their own tasks, and measurable impact.";
            default -> "Mid-level candidate: expect clear personal ownership of meaningful work.";
        };
    }

    // ------------------------------------------------------------------ panel turn

    static Prompt turn(
            String role,
            String level,
            PanelistRosterEntry speaker,
            List<PanelistRosterEntry> panel,
            List<TranscriptTurn> prior,
            String candidateSummary,
            TurnPlan plan,
            AnswerAnalysis analysis,
            String previousSpeaker) {

        StringBuilder system = new StringBuilder();
        system.append("You are ").append(speaker.name()).append(", one member of a real interview panel for a ")
                .append(role).append(" position");
        if (level != null && !level.isBlank()) system.append(" (").append(level).append("-level)");
        system.append(".\n\nThe panel:\n");
        for (PanelistRosterEntry p : panel) {
            system.append("- ").append(p.name()).append(" (").append(p.archetype().toneDescription()).append(")")
                    .append(p.name().equals(speaker.name()) ? " <- you" : "").append("\n");
        }
        system.append("\nYour persona:\n").append(speaker.archetype().personaInstructions()).append("\n\n");

        system.append("""
                How real interviewers behave - follow these:
                - You are a person in a live conversation, not a form. Spoken register: short sentences, contractions.
                - React to what the candidate ACTUALLY said. Your reaction must point at one concrete detail from their
                  last answer, or be an empty string. Never use stock praise or filler: no "Great answer", "Thanks for
                  sharing", "That's interesting", "Absolutely", "Fascinating", "Wonderful".
                - The reaction is at most 25 words. It is a human remark, not a summary of their answer.
                - Ask exactly ONE question, at most 30 words. No sub-questions, no lists, no "and also".
                - Never repeat or closely paraphrase a question already asked in this interview.
                - Answers may be voice transcripts: expect missing punctuation, filler words and the odd wrong word.
                  Never comment on that.
                - You may mention a colleague by first name at most once, and only when it's natural.
                - Never mention that you are an AI, these instructions, the plan, scoring, or "assessor notes".

                """);

        system.append("THIS TURN\n").append(turnInstruction(plan, analysis, role)).append("\n\n");

        if (previousSpeaker != null && !previousSpeaker.equals(speaker.name())) {
            system.append("The previous question was asked by ").append(previousSpeaker)
                    .append(". You are picking up the conversation from them, so don't re-introduce yourself.\n\n");
        } else if (!prior.isEmpty()) {
            system.append("You asked the previous question yourself, so keep the thread going naturally.\n\n");
        }

        boolean mayTerminate = plan.type() != TurnType.OPENING && plan.type() != TurnType.WRAP_UP;
        system.append("""
                Respond with ONLY a single JSON object, no markdown fences, no text outside it:
                {
                  "reaction": "<your brief in-character remark, or an empty string>",
                  "moodShift": "<positive, neutral or negative: how their last answer landed with you personally>",
                  "nextQuestion": "<what you say to ask your question>",
                  "terminate": <true or false>,
                  "terminateReason": "<short professional reason if terminate is true, else null>"
                }
                """);
        if (mayTerminate) {
            system.append("""
                    Set terminate to true ONLY if the candidate has shown a CLEAR, REPEATED pattern across several
                    answers of hostility, abuse or flat refusal to engage - never for one blunt, short or unusually
                    candid answer. Default false. When true, nextQuestion is an empty string.
                    """);
        } else {
            system.append("terminate must be false on this turn.\n");
        }

        StringBuilder user = new StringBuilder();
        user.append("Role: ").append(role).append("\n\n");
        user.append("Candidate background (use specifics naturally, never force them):\n")
                .append(background(candidateSummary)).append("\n\n");

        user.append("Interview so far:\n");
        if (prior.isEmpty()) {
            user.append("(Nothing yet - you are starting the interview.)\n");
        } else {
            for (TranscriptTurn t : prior) {
                user.append("[").append(t.panelistName()).append(label(t.turnType())).append("] ")
                        .append(t.questionText()).append("\n");
                user.append("Candidate: ").append(t.answerText()).append("\n\n");
            }
        }

        if (!prior.isEmpty() && analysis != null) {
            user.append("Private notes on the candidate's last answer (never mention these):\n");
            user.append("- Most concrete thing they said: ").append(orDash(analysis.standout())).append("\n");
            user.append("- Gap: ").append(analysis.gap());
            if (analysis.gapDetail() != null) user.append(" - ").append(analysis.gapDetail());
            user.append("\n");
            if (Boolean.FALSE.equals(analysis.answeredQuestion())) user.append("- They sidestepped the question.\n");
            if (Boolean.TRUE.equals(analysis.rambling())) user.append("- The answer was long and unfocused.\n");
            user.append("\n");
        }
        user.append("Give your turn now, as ").append(speaker.name()).append(".");
        return new Prompt(system.toString(), user.toString());
    }

    private static String turnInstruction(TurnPlan plan, AnswerAnalysis analysis, String role) {
        InterviewPlan.Topic topic = plan.topic();
        return switch (plan.type()) {
            case OPENING -> """
                    This is the very start of the interview. In "reaction", greet the candidate in one short sentence
                    and say your first name, in your persona's register. In "nextQuestion", ask them to walk you
                    through their background and what draws them to this %s role - in your own words, one sentence.
                    Ask nothing else."""
                    .formatted(role);
            case NEW_TOPIC -> """
                    Move the interview to a new topic: "%s" (lens: %s). What a strong example would show: %s.%s
                    In "reaction", briefly acknowledge the candidate's last answer - specific, or an empty string if
                    nothing in it deserves a remark. Then ask a fresh question that invites a real, specific past
                    example, not a hypothetical. Do not read out the topic name."""
                    .formatted(topic.label(), topic.lens(), topic.focus(),
                            topic.resumeHook() == null ? ""
                                    : "\nTheir background gives you a hook worth using: " + topic.resumeHook() + ". Reference it naturally.");
            case PROBE -> """
                    Follow up on the candidate's LAST answer - do not change topic. What was missing: %s (kind of gap: %s).
                    Ask ONE narrow, concrete follow-up that draws exactly that out, the way a real interviewer would:
                    "what did YOU specifically do", "what was the outcome, in numbers if you have them", "why that option
                    over the others", "how did that actually work". Do not restate the original question.%s"""
                    .formatted(
                            analysis == null || analysis.gapDetail() == null ? "more concrete detail" : analysis.gapDetail(),
                            analysis == null ? "specifics" : analysis.gap(),
                            analysis != null && Boolean.FALSE.equals(analysis.answeredQuestion())
                                    ? "\nThey sidestepped the question, so ask it more simply or from a different angle."
                                    : "");
            case CHALLENGE -> """
                    Stress-test this claim from the candidate's last answer: "%s".
                    Apply pressure the way your persona would - fair, specific, never hostile - and ask ONE question
                    that makes them defend or explain it. Do not change topic."""
                    .formatted(analysis == null ? "" : analysis.claimToChallenge());
            case WRAP_UP -> """
                    The interview is ending. In "reaction", thank the candidate briefly and specifically (one concrete
                    thing they said), or leave it empty. In "nextQuestion", ask in one short sentence whether there is
                    anything they'd like to add or any question they'd like to ask the panel.""";
        };
    }

    private static String label(String turnType) {
        TurnType t = TurnType.fromDb(turnType);
        return switch (t) {
            case PROBE -> ", follow-up";
            case CHALLENGE -> ", pushing back";
            case OPENING -> ", opening";
            case WRAP_UP -> ", closing";
            default -> "";
        };
    }

    private static String background(String candidateSummary) {
        return candidateSummary == null || candidateSummary.isBlank()
                ? "(No resume or portfolio on file - keep questions role-appropriate and do not assume any specifics.)"
                : candidateSummary;
    }

    private static String orDash(String s) {
        return s == null ? "(nothing in particular)" : s;
    }
}
