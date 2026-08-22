package com.preppanel.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates LLM calls with a simple strategy/fallback pattern: try the configured
 * primary provider, and on any failure (timeout, rate-limit, malformed response) fall
 * back to the secondary provider. This is the "resilient multi-provider LLM client"
 * piece from the PRD - deliberately simple for v1 (no circuit breaker yet), but the
 * LlmClientService interface means resilience4j or a smarter retry policy can be
 * dropped in later without touching callers.
 */
@Service
@Slf4j
public class LlmService {

    private final Map<String, LlmClientService> clientsByProvider;
    private final LlmProperties properties;

    public LlmService(List<LlmClientService> clients, LlmProperties properties) {
        this.clientsByProvider = clients.stream()
                .collect(java.util.stream.Collectors.toMap(LlmClientService::providerName, c -> c));
        this.properties = properties;
    }

    /**
     * Generates a single behavioral interview question tailored to the given role and
     * tone preset, avoiding repeats of what's already been asked in this session.
     */
    public String generateBehavioralQuestion(String role, List<String> alreadyAsked, String tone) {
        String systemPrompt = """
                You are an experienced technical interviewer conducting a behavioral interview.
                Generate exactly ONE behavioral interview question - no preamble, no numbering,
                no quotation marks, just the question text itself, one sentence or two at most.
                The question should be realistic for a real interview and probe how the candidate
                has handled a real past situation (e.g. conflict, failure, prioritization, leadership).

                %s
                """.formatted(tonePromptFragment(tone));

        String askedList = alreadyAsked.isEmpty()
                ? "(none yet)"
                : String.join("\n- ", alreadyAsked);

        String userPrompt = """
                Candidate is interviewing for this role: %s

                Questions already asked in this session (do not repeat these or ask something
                very similar):
                - %s

                Generate the next behavioral question now.
                """.formatted(role, askedList);

        return completeWithFallback(systemPrompt, userPrompt);
    }

    /** Maps a tone preset to a short, concrete phrasing instruction for the model. */
    private String tonePromptFragment(String tone) {
        String normalized = tone == null ? "neutral" : tone.toLowerCase().trim();
        return switch (normalized) {
            case "friendly" -> "Tone: warm and encouraging. Open with brief, genuine warmth "
                    + "before the question. Phrase it like a supportive interviewer who wants "
                    + "the candidate to succeed.";
            case "direct" -> "Tone: direct and no-nonsense. No pleasantries, no warm-up - just "
                    + "the question, phrased tersely and precisely, the way a busy senior "
                    + "interviewer with high standards would ask it.";
            default -> "Tone: neutral and professional. Standard, even-keeled interviewer phrasing "
                    + "- neither warm nor terse.";
        };
    }

    /**
     * Generates one conversational turn from a specific panelist: an optional brief
     * in-character reaction to the candidate's previous answer, an assessment of
     * whether the panel should end the interview early, and the next question.
     *
     * This is the core of the "real panel, not a chatbot" behavior - the model sees
     * the full transcript so far (not just prior question text) and is explicitly
     * told to react to specifics, not just avoid repeating itself. Returns the raw
     * JSON text; the caller (InterviewService) parses and validates it defensively,
     * same pattern as ScoringService.
     */
    public String generatePanelTurn(
            String role,
            String experienceLevel,
            PanelistRosterEntry speaker,
            List<PanelistRosterEntry> fullPanel,
            List<TranscriptTurn> priorTurns
    ) {
        String panelRoster = fullPanel.stream()
                .map(p -> "- " + p.name() + " (" + p.archetype().toneDescription() + ")")
                .reduce("", (a, b) -> a + b + "\n");

        String experienceLine = (experienceLevel == null || experienceLevel.isBlank())
                ? ""
                : "The candidate is targeting a " + experienceLevel + "-level role - calibrate question "
                        + "difficulty and depth accordingly.\n";

        String systemPrompt = """
                You are %s, one panelist on a real interview panel alongside colleagues:
                %s
                %s

                Persona: %s

                You are conducting a real, in-person-feeling behavioral interview - not filling out a
                form. Speak and think like a real person on this panel, in character at all times.

                Respond with ONLY a single JSON object, no markdown fences, no prose outside it:
                {
                  "reaction": "<1 short, natural sentence reacting to their previous answer - reference
                      something SPECIFIC they said. Empty string "" if this is the first question of
                      the interview.>",
                  "moodShift": "<one of: positive, neutral, negative - how their previous answer landed
                      with you specifically>",
                  "nextQuestion": "<the next interview question in your voice, one or two sentences.
                      Empty string "" if you are ending the interview (terminate=true).>",
                  "terminate": <true only if the candidate has shown a CLEAR, REPEATED pattern across
                      multiple answers of hostility, abuse, or flat refusal to engage - never for a
                      single blunt, short, or unusually candid answer. Default false.>,
                  "terminateReason": "<short, professional reason if terminate=true, else null>"
                }
                """.formatted(speaker.name(), panelRoster, experienceLine, speaker.archetype().personaInstructions());

        StringBuilder transcript = new StringBuilder();
        if (priorTurns.isEmpty()) {
            transcript.append("(This is the first question - no prior turns yet.)\n");
        } else {
            for (TranscriptTurn turn : priorTurns) {
                transcript.append(turn.panelistName()).append(": ").append(turn.questionText()).append("\n");
                transcript.append("Candidate: ").append(turn.answerText()).append("\n\n");
            }
        }

        String userPrompt = """
                Role being interviewed for: %s

                Transcript so far:
                %s

                Generate your next turn now, as %s.
                """.formatted(role, transcript, speaker.name());

        return completeWithFallback(systemPrompt, userPrompt);
    }

    /**
     * Sends a single Q&A pair to the LLM as a judge and returns its raw text response,
     * which the caller (ScoringService) is responsible for parsing as JSON and validating -
     * never trust LLM output shape blindly.
     */
    public String scoreAnswer(String role, String questionText, String answerText) {
        String systemPrompt = """
                You are an expert interview coach scoring a candidate's answer to a behavioral
                interview question. Respond with ONLY a single JSON object, no markdown fences,
                no prose before or after it. The JSON must have exactly these fields:
                {
                  "contentScore": <integer 0-100, how relevant and substantive the answer is>,
                  "structureScore": <integer 0-100, how well it follows a clear structure like STAR>,
                  "technicalScore": null,
                  "feedbackText": "<2-4 sentences of specific, constructive feedback>"
                }
                Do not include any text outside the JSON object.
                """;

        String userPrompt = """
                Role: %s
                Question: %s
                Candidate's answer: %s

                Score this answer now.
                """.formatted(role, questionText, answerText);

        return completeWithFallback(systemPrompt, userPrompt);
    }

    private String completeWithFallback(String systemPrompt, String userPrompt) {
        String primary = properties.primaryProvider();
        String fallback = properties.fallbackProvider();

        try {
            return call(primary, systemPrompt, userPrompt);
        } catch (Exception primaryFailure) {
            log.warn("LLM primary provider '{}' failed, falling back to '{}': {}",
                    primary, fallback, primaryFailure.getMessage());
            try {
                return call(fallback, systemPrompt, userPrompt);
            } catch (Exception fallbackFailure) {
                log.error("LLM fallback provider '{}' also failed", fallback, fallbackFailure);
                throw new LlmProviderException("all",
                        "Both primary (" + primary + ") and fallback (" + fallback + ") providers failed",
                        fallbackFailure);
            }
        }
    }

    private String call(String provider, String systemPrompt, String userPrompt) {
        LlmClientService client = clientsByProvider.get(provider);
        if (client == null) {
            throw new LlmProviderException(provider, "No LlmClientService registered for provider: " + provider);
        }
        return client.complete(systemPrompt, userPrompt);
    }
}
