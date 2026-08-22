package com.preppanel.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preppanel.exception.ApiException;
import com.preppanel.interview.dto.*;
import com.preppanel.llm.LlmService;
import com.preppanel.llm.PanelistRosterEntry;
import com.preppanel.llm.TranscriptTurn;
import com.preppanel.panel.PanelistArchetype;
import com.preppanel.question.QuestionBank;
import com.preppanel.question.QuestionBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {

    /** v1: fixed-length sessions. Company research / dynamic length come later. */
    private static final int QUESTIONS_PER_SESSION = 5;
    private static final String DEFAULT_QUESTION_TYPE = "behavioral";
    private static final int MAX_PANELISTS = 4;
    private static final java.util.Set<String> VALID_TONES = java.util.Set.of("friendly", "neutral", "direct");
    private static final java.util.Set<String> VALID_EXPERIENCE_LEVELS =
            java.util.Set.of("junior", "mid", "senior", "staff");

    private final InterviewSessionRepository sessionRepository;
    private final SessionQuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionBankRepository questionBankRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SessionResponse createSession(UUID userId, CreateSessionRequest request) {
        List<PanelistSelection> panelists = validatePanelists(request.panelists());

        InterviewSession session = InterviewSession.builder()
                .userId(userId)
                .role(request.role())
                .mode(request.mode() == null ? "text" : request.mode())
                .tone(normalizeTone(request.tone()))
                .panelists(serializePanelists(panelists))
                .experienceLevel(normalizeExperienceLevel(request.experienceLevel()))
                .status("in_progress")
                .terminatedByPanel(false)
                .build();

        session = sessionRepository.save(session);

        TurnOutcome outcome = generateNextTurn(session, 1);
        // A panel terminating on question 1 would be a prompt/model bug, not a real
        // scenario - but handle it defensively rather than NPE-ing on a null question.
        if (outcome.terminated()) {
            session.setStatus("completed");
            session.setTerminatedByPanel(true);
            session.setTerminationReason(outcome.terminationReason());
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            return buildSessionResponse(session, null, true);
        }

        return buildSessionResponse(session, toQuestionResponse(outcome.question()), false);
    }

    @Transactional
    public SessionResponse submitAnswer(UUID userId, UUID sessionId, SubmitAnswerRequest request) {
        InterviewSession session = getOwnedSession(userId, sessionId);

        if ("completed".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This interview session is already completed");
        }

        int currentSeq = questionRepository.countBySessionId(sessionId);
        SessionQuestion currentQuestion = questionRepository.findBySessionIdAndSequenceNum(sessionId, currentSeq)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No active question for session"));

        if (answerRepository.findByQuestionId(currentQuestion.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This question has already been answered");
        }

        Answer answer = Answer.builder()
                .questionId(currentQuestion.getId())
                .answerText(request.answerText())
                .build();
        answerRepository.save(answer);

        if (currentSeq >= QUESTIONS_PER_SESSION) {
            session.setStatus("completed");
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            return buildSessionResponse(session, null, true);
        }

        TurnOutcome outcome = generateNextTurn(session, currentSeq + 1);

        if (outcome.terminated()) {
            session.setStatus("completed");
            session.setTerminatedByPanel(true);
            session.setTerminationReason(outcome.terminationReason());
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            return buildSessionResponse(session, null, true);
        }

        return buildSessionResponse(session, toQuestionResponse(outcome.question()), false);
    }

    @Transactional
    public SessionResponse completeSession(UUID userId, UUID sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        session.setStatus("completed");
        session.setCompletedAt(Instant.now());
        sessionRepository.save(session);
        return buildSessionResponse(session, null, true);
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse getSessionDetail(UUID userId, UUID sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        List<SessionQuestion> questions = questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);

        List<SessionDetailResponse.QuestionAndAnswer> items = questions.stream()
                .map(q -> new SessionDetailResponse.QuestionAndAnswer(
                        q.getId(),
                        q.getSequenceNum(),
                        q.getQuestionText(),
                        q.getQuestionType(),
                        q.getPanelistName(),
                        q.getReactionText(),
                        answerRepository.findByQuestionId(q.getId()).map(Answer::getAnswerText).orElse(null)
                ))
                .toList();

        return new SessionDetailResponse(
                session.getId(), session.getRole(), session.getMode(), session.getTone(), session.getStatus(),
                session.getStartedAt(), session.getCompletedAt(), items
        );
    }

    // --- turn generation -----------------------------------------------------------

    private record TurnOutcome(SessionQuestion question, boolean terminated, String terminationReason) {
        static TurnOutcome of(SessionQuestion question) {
            return new TurnOutcome(question, false, null);
        }
        static TurnOutcome terminated(String reason) {
            return new TurnOutcome(null, true, reason);
        }
    }

    /**
     * Generates the next turn. If the session has a cast panel, this is a full
     * conversational turn (reaction + mood + next question, possibly early
     * termination). If no panel was cast, this falls back to the original
     * single-tone question generator for backward compatibility.
     */
    private TurnOutcome generateNextTurn(InterviewSession session, int sequenceNum) {
        List<PanelistSelection> panelists = deserializePanelists(session.getPanelists());

        if (panelists.isEmpty()) {
            return TurnOutcome.of(generateLegacyToneQuestion(session, sequenceNum));
        }

        return generatePanelTurn(session, sequenceNum, panelists);
    }

    private SessionQuestion generateLegacyToneQuestion(InterviewSession session, int sequenceNum) {
        List<String> alreadyAsked = questionRepository.findBySessionIdOrderBySequenceNumAsc(session.getId()).stream()
                .map(SessionQuestion::getQuestionText)
                .toList();

        try {
            String questionText = llmService.generateBehavioralQuestion(session.getRole(), alreadyAsked, session.getTone());
            SessionQuestion question = SessionQuestion.builder()
                    .sessionId(session.getId())
                    .questionBankId(null)
                    .sequenceNum(sequenceNum)
                    .questionText(questionText)
                    .questionType(DEFAULT_QUESTION_TYPE)
                    .source("llm_generated")
                    .build();
            return questionRepository.save(question);
        } catch (Exception llmFailure) {
            log.warn("LLM question generation failed, falling back to question bank: {}", llmFailure.getMessage());
            return questionRepository.save(pickFromBank(session.getId(), sequenceNum));
        }
    }

    private TurnOutcome generatePanelTurn(InterviewSession session, int sequenceNum, List<PanelistSelection> panelistSelections) {
        UUID sessionId = session.getId();

        List<PanelistRosterEntry> roster = panelistSelections.stream()
                .map(sel -> new PanelistRosterEntry(resolvePanelistName(sel), PanelistArchetype.byId(sel.archetypeId())))
                .toList();

        int speakerIndex = (sequenceNum - 1) % roster.size();
        PanelistRosterEntry speaker = roster.get(speakerIndex);

        List<SessionQuestion> priorQuestions = questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
        List<TranscriptTurn> transcript = priorQuestions.stream()
                .map(pq -> {
                    Answer ans = answerRepository.findByQuestionId(pq.getId()).orElse(null);
                    String label = pq.getPanelistName() != null ? pq.getPanelistName() : "Interviewer";
                    return new TranscriptTurn(label, pq.getQuestionText(), ans == null ? "" : ans.getAnswerText());
                })
                .toList();

        try {
            String raw = llmService.generatePanelTurn(
                    session.getRole(), session.getExperienceLevel(), speaker, roster, transcript);
            String cleaned = stripMarkdownFences(raw);
            com.preppanel.llm.dto.PanelTurnJudgment judgment =
                    objectMapper.readValue(cleaned, com.preppanel.llm.dto.PanelTurnJudgment.class);

            boolean terminate = Boolean.TRUE.equals(judgment.terminate());
            if (terminate) {
                String reason = judgment.terminateReason() == null || judgment.terminateReason().isBlank()
                        ? "The panel ended the interview early."
                        : judgment.terminateReason();
                return TurnOutcome.terminated(reason);
            }

            if (judgment.nextQuestion() == null || judgment.nextQuestion().isBlank()) {
                throw new IllegalArgumentException("Empty nextQuestion with terminate=false");
            }

            SessionQuestion question = SessionQuestion.builder()
                    .sessionId(sessionId)
                    .questionBankId(null)
                    .sequenceNum(sequenceNum)
                    .questionText(judgment.nextQuestion())
                    .questionType(DEFAULT_QUESTION_TYPE)
                    .source("llm_generated")
                    .panelistArchetypeId(speaker.archetype().id())
                    .panelistName(speaker.name())
                    .reactionText(judgment.reaction())
                    .moodShift(normalizeMoodShift(judgment.moodShift()))
                    .build();

            return TurnOutcome.of(questionRepository.save(question));
        } catch (Exception llmFailure) {
            log.warn("Panel turn generation failed, falling back to question bank: {}", llmFailure.getMessage());
            SessionQuestion fallback = pickFromBank(sessionId, sequenceNum);
            // Still attribute it to the speaking panelist so the UI has someone to show,
            // even though the bank question has no personality/reaction of its own.
            fallback.setPanelistArchetypeId(speaker.archetype().id());
            fallback.setPanelistName(speaker.name());
            return TurnOutcome.of(questionRepository.save(fallback));
        }
    }

    private SessionQuestion pickFromBank(UUID sessionId, int sequenceNum) {
        QuestionBank picked = questionBankRepository.findRandomUnusedForSession(sessionId, DEFAULT_QUESTION_TYPE);

        if (picked == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Question bank exhausted for type " + DEFAULT_QUESTION_TYPE + " - add more seed questions");
        }

        return SessionQuestion.builder()
                .sessionId(sessionId)
                .questionBankId(picked.getId())
                .sequenceNum(sequenceNum)
                .questionText(picked.getQuestionText())
                .questionType(picked.getQuestionType())
                .source("bank")
                .build();
    }

    /** Some models wrap JSON in ```json ... ``` even when told not to - strip it defensively. */
    private String stripMarkdownFences(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(json)?", "").trim();
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    // --- panelist helpers ------------------------------------------------------------

    private List<PanelistSelection> validatePanelists(List<PanelistSelection> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of(); // legacy single-tone path
        }
        if (requested.size() > MAX_PANELISTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A panel can have at most " + MAX_PANELISTS + " panelists");
        }
        return requested;
    }

    private String resolvePanelistName(PanelistSelection selection) {
        if (selection.name() != null && !selection.name().isBlank()) {
            return selection.name().trim();
        }
        return PanelistArchetype.byId(selection.archetypeId()).displayName();
    }

    private String serializePanelists(List<PanelistSelection> panelists) {
        try {
            return objectMapper.writeValueAsString(panelists == null ? List.of() : panelists);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<PanelistSelection> deserializePanelists(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<PanelistSelection>>() {});
        } catch (Exception e) {
            log.warn("Could not deserialize panelists JSON, treating as empty: {}", e.getMessage());
            return List.of();
        }
    }

    private String normalizeTone(String requestedTone) {
        if (requestedTone == null) return "neutral";
        String lower = requestedTone.toLowerCase().trim();
        return VALID_TONES.contains(lower) ? lower : "neutral";
    }

    private String normalizeMoodShift(String requested) {
        if (requested == null) return "neutral";
        String lower = requested.toLowerCase().trim();
        return java.util.Set.of("positive", "neutral", "negative").contains(lower) ? lower : "neutral";
    }

    private String normalizeExperienceLevel(String requested) {
        if (requested == null) return null;
        String lower = requested.toLowerCase().trim();
        return VALID_EXPERIENCE_LEVELS.contains(lower) ? lower : null;
    }

    // --- response building -------------------------------------------------------------

    private SessionResponse buildSessionResponse(InterviewSession session, QuestionResponse currentQuestion, boolean completed) {
        return new SessionResponse(
                session.getId(),
                session.getRole(),
                session.getMode(),
                session.getTone(),
                deserializePanelists(session.getPanelists()),
                session.getStatus(),
                currentQuestion,
                completed,
                session.isTerminatedByPanel(),
                session.getTerminationReason()
        );
    }

    private InterviewSession getOwnedSession(UUID userId, UUID sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getUserId().equals(userId)) {
            // 404 rather than 403 - don't reveal that a session ID exists for another user.
            throw new ApiException(HttpStatus.NOT_FOUND, "Session not found");
        }

        return session;
    }

    private QuestionResponse toQuestionResponse(SessionQuestion q) {
        return new QuestionResponse(
                q.getId(), q.getSequenceNum(), q.getQuestionText(), q.getQuestionType(),
                q.getPanelistArchetypeId(), q.getPanelistName(), q.getReactionText(), q.getMoodShift()
        );
    }
}
