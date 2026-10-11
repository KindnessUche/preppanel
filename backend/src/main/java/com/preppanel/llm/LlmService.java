package com.preppanel.llm;

import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.InterviewPlan;
import com.preppanel.llm.dto.TurnPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
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

    private final Map<String, LlmClientService> clientsByProvider = new HashMap<>();
    private final LlmProperties properties;
    private final RequestCredentials requestCredentials;

    public LlmService(List<LlmClientService> clients, LlmProperties properties, RequestCredentials requestCredentials) {
        for (LlmClientService client : clients) {
            for (String provider : client.providers()) {
                clientsByProvider.put(provider, client);
            }
        }
        this.properties = properties;
        this.requestCredentials = requestCredentials;
    }

    /**
     * Generates a single behavioral interview question tailored to the given role and
     * tone preset, avoiding repeats of what's already been asked in this session.
     */
    public String generateBehavioralQuestion(String role, List<String> alreadyAsked, String tone, String candidateSummary) {
        String systemPrompt = """
                You are an experienced technical interviewer conducting a behavioral interview.
                Generate exactly ONE behavioral interview question - no preamble, no numbering,
                no quotation marks, just the question text itself, one sentence or two at most.
                The question should be realistic for a real interview and probe how the candidate
                has handled a real past situation (e.g. conflict, failure, prioritization, leadership).
                Where natural, tailor it to the candidate's actual background below rather than
                asking something generic - but don't force a connection that isn't there.

                %s
                """.formatted(tonePromptFragment(tone));

        String askedList = alreadyAsked.isEmpty()
                ? "(none yet)"
                : String.join("\n- ", alreadyAsked);

        String backgroundLine = (candidateSummary == null || candidateSummary.isBlank())
                ? "(No resume/portfolio on file - ask a role-appropriate general question.)"
                : candidateSummary;

        String userPrompt = """
                Candidate is interviewing for this role: %s

                Candidate background:
                %s

                Questions already asked in this session (do not repeat these or ask something
                very similar):
                - %s

                Generate the next behavioral question now.
                """.formatted(role, backgroundLine, askedList);

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

    /** Designs the interview: the topics the panel will cover. Returns raw JSON; the caller validates it. */
    public String generateInterviewPlan(String role, String experienceLevel, String candidateSummary,
                                        List<PanelistRosterEntry> roster, int topicCount) {
        InterviewPrompts.Prompt p = InterviewPrompts.plan(role, experienceLevel, candidateSummary, roster, topicCount);
        return completeWithFallback(p.system(), p.user());
    }

    /**
     * The hidden assessor: reads the candidate's latest answer (last element of topicTurns) and reports its
     * biggest gap, anything worth challenging, and the most concrete thing said. Returns raw JSON.
     */
    public String assessAnswer(String role, String experienceLevel, InterviewPlan.Topic topic,
                               List<TranscriptTurn> topicTurns, String candidateSummary) {
        InterviewPrompts.Prompt p = InterviewPrompts.assess(role, experienceLevel, topic, topicTurns, candidateSummary);
        return completeWithFallback(p.system(), p.user());
    }

    /**
     * Generates one conversational turn from a specific panelist. The engine has already decided WHAT kind of
     * turn this is (opening, new topic, follow-up probe, challenge, wrap-up) and WHO speaks; the model's job is
     * to say it in that person's voice. Returns raw JSON; the caller (InterviewService) parses it defensively.
     */
    public String generatePanelTurn(
            String role,
            String experienceLevel,
            PanelistRosterEntry speaker,
            List<PanelistRosterEntry> fullPanel,
            List<TranscriptTurn> priorTurns,
            String candidateSummary,
            TurnPlan plan,
            AnswerAnalysis analysis,
            String previousSpeaker
    ) {
        InterviewPrompts.Prompt p = InterviewPrompts.turn(
                role, experienceLevel, speaker, fullPanel, priorTurns, candidateSummary, plan, analysis, previousSpeaker);
        return completeWithFallback(p.system(), p.user());
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

                If the question starts with "[Follow-up]", it is a short, narrow follow-up to an earlier answer, not
                a fresh behavioral question. Judge it as one: contentScore is how directly and specifically it answers
                what was asked; structureScore is how clear and well-organised the reply is, NOT whether it contains a
                full STAR story.
                """;

        String userPrompt = """
                Role: %s
                Question: %s
                Candidate's answer: %s

                Score this answer now.
                """.formatted(role, questionText, answerText);

        return completeWithFallback(systemPrompt, userPrompt);
    }

    /**
     * Picks whose key to use, in order of preference: the key the user set in Settings (sent with
     * this request), else the optional server-side defaults (GROQ_API_KEY / GEMINI_API_KEY env vars,
     * handy for local dev). With server defaults we still fall back primary -> secondary.
     */
    private List<LlmCredentials> candidates() {
        var fromUser = requestCredentials.llm();
        if (fromUser.isPresent()) {
            LlmCredentials creds = fromUser.get();
            if (!clientsByProvider.containsKey(creds.provider())) {
                throw new LlmConfigException(HttpStatus.BAD_REQUEST, "Unknown AI provider: " + creds.provider());
            }
            return List.of(creds);
        }

        List<LlmCredentials> server = new ArrayList<>();
        for (String provider : List.of(properties.primaryProvider(), properties.fallbackProvider())) {
            if ("groq".equals(provider) && properties.groq() != null && notBlank(properties.groq().apiKey())) {
                server.add(new LlmCredentials("groq", properties.groq().apiKey(), properties.groq().model()));
            } else if ("gemini".equals(provider) && properties.gemini() != null && notBlank(properties.gemini().apiKey())) {
                server.add(new LlmCredentials("gemini", properties.gemini().apiKey(), properties.gemini().model()));
            }
        }
        if (server.isEmpty()) {
            throw new LlmConfigException(HttpStatus.BAD_REQUEST,
                    "No AI provider is set up yet. Open Account → API keys and add your Claude, ChatGPT, Gemini or Grok key.");
        }
        return server;
    }

    private String completeWithFallback(String systemPrompt, String userPrompt) {
        List<LlmCredentials> candidates = candidates();
        RuntimeException last = null;

        for (int i = 0; i < candidates.size(); i++) {
            LlmCredentials creds = candidates.get(i);
            try {
                return clientsByProvider.get(creds.provider()).complete(systemPrompt, userPrompt, creds);
            } catch (RuntimeException e) {
                last = e;
                if (i < candidates.size() - 1) {
                    log.warn("LLM provider '{}' failed, trying next: {}", creds.provider(), e.getMessage());
                }
            }
        }
        throw last;
    }

    /** Cheap connectivity check used by the Settings page's "Test" button. */
    public String ping() {
        return completeWithFallback(
                "You are a connectivity check. Reply with the single word OK.", "ping");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
