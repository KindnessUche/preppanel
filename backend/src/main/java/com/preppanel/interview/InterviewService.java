package com.preppanel.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preppanel.exception.ApiException;
import com.preppanel.interview.dto.*;
import com.preppanel.llm.LlmJson;
import com.preppanel.llm.LlmService;
import com.preppanel.llm.PanelistRosterEntry;
import com.preppanel.llm.TranscriptTurn;
import com.preppanel.llm.dto.AnswerAnalysis;
import com.preppanel.llm.dto.InterviewPlan;
import com.preppanel.llm.dto.TurnPlan;
import com.preppanel.llm.dto.TurnType;
import com.preppanel.panel.PanelistArchetype;
import com.preppanel.profile.ProfileService;
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

    /** Only the legacy single-tone path (no panel) uses a fixed length; panel interviews follow their plan (see TurnDirector). */
    private static final int QUESTIONS_PER_SESSION = 5;
    private static final int TOPICS_FOR_JUNIOR_AND_MID = 4;
    private static final int TOPICS_FOR_SENIOR_AND_STAFF = 5;
    /** Answers shorter than this skip the assessor call: there is nothing to analyse, and it saves a round trip. */
    private static final int MIN_WORDS_WORTH_ASSESSING = 5;
    private static final String OPENING_FALLBACK =
            "Thanks for joining us. To start, could you walk us through your background and what draws you to this role?";
    private static final String WRAP_UP_FALLBACK =
            "That's everything from us. Is there anything you'd like to add, or any questions for the panel?";
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
    private final ProfileService profileService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SessionResponse createSession(UUID userId, CreateSessionRequest request) {
        List<PanelistSelection> panelists = validatePanelists(request.panelists());
        String experienceLevel = normalizeExperienceLevel(request.experienceLevel());

        // Plan first, then save: if the AI key is missing or rejected this throws before any session row exists.
        String planJson = panelists.isEmpty() ? null : serializePlan(buildPlan(userId, request.role(), experienceLevel, panelists));

        InterviewSession session = InterviewSession.builder()
                .userId(userId)
                .role(request.role())
                .mode(request.mode() == null ? "text" : request.mode())
                .tone(normalizeTone(request.tone()))
                .panelists(serializePanelists(panelists))
                .plan(planJson)
                .experienceLevel(experienceLevel)
                .status("in_progress")
                .terminatedByPanel(false)
                .build();

        session = sessionRepository.save(session);

        TurnOutcome outcome = generateNextTurn(userId, session, 1);
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
                .audioMetrics(serializeAudioMetrics(request))
                .build();
        answerRepository.save(answer);

        boolean panelSession = !deserializePanelists(session.getPanelists()).isEmpty();
        boolean finished;
        if (panelSession) {
            // The wrap-up question is always last. Every other answer gets a hidden assessment first.
            finished = TurnType.fromDb(currentQuestion.getTurnType()) == TurnType.WRAP_UP;
            if (!finished) assessAndStore(userId, session, currentQuestion, answer);
        } else {
            finished = currentSeq >= QUESTIONS_PER_SESSION;
        }

        if (finished) {
            session.setStatus("completed");
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            return buildSessionResponse(session, null, true);
        }

        TurnOutcome outcome = generateNextTurn(userId, session, currentSeq + 1);

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

    /** null for typed answers; JSON string for voice answers (the column is jsonb). */
    private String serializeAudioMetrics(SubmitAnswerRequest request) {
        if (request.audioMetrics() == null) return null;
        try {
            return objectMapper.writeValueAsString(request.audioMetrics());
        } catch (Exception e) {
            log.warn("Could not serialize audio metrics, storing answer without them: {}", e.getMessage());
            return null;
        }
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
    private TurnOutcome generateNextTurn(UUID userId, InterviewSession session, int sequenceNum) {
        List<PanelistSelection> panelists = deserializePanelists(session.getPanelists());
        String candidateSummary = profileService.getCandidateSummaryForPrompt(userId);

        if (panelists.isEmpty()) {
            return TurnOutcome.of(generateLegacyToneQuestion(session, sequenceNum, candidateSummary));
        }

        return generatePanelTurn(session, sequenceNum, panelists, candidateSummary);
    }

    private SessionQuestion generateLegacyToneQuestion(InterviewSession session, int sequenceNum, String candidateSummary) {
        List<String> alreadyAsked = questionRepository.findBySessionIdOrderBySequenceNumAsc(session.getId()).stream()
                .map(SessionQuestion::getQuestionText)
                .toList();

        try {
            String questionText = llmService.generateBehavioralQuestion(
                    session.getRole(), alreadyAsked, session.getTone(), candidateSummary);
            SessionQuestion question = SessionQuestion.builder()
                    .sessionId(session.getId())
                    .questionBankId(null)
                    .sequenceNum(sequenceNum)
                    .questionText(questionText)
                    .questionType(DEFAULT_QUESTION_TYPE)
                    .source("llm_generated")
                    .build();
            return questionRepository.save(question);
        } catch (ApiException configProblem) {
            throw configProblem; // e.g. no/invalid API key - tell the user instead of silently using the bank
        } catch (Exception llmFailure) {
            log.warn("LLM question generation failed, falling back to question bank: {}", llmFailure.getMessage());
            return questionRepository.save(pickFromBank(session.getId(), sequenceNum));
        }
    }

    private TurnOutcome generatePanelTurn(InterviewSession session, int sequenceNum, List<PanelistSelection> panelistSelections, String candidateSummary) {
        UUID sessionId = session.getId();
        List<PanelistRosterEntry> roster = rosterOf(panelistSelections);
        InterviewPlan plan = loadPlan(session);

        List<SessionQuestion> priorQuestions = questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
        List<TranscriptTurn> transcript = priorQuestions.stream().map(this::toTranscriptTurn).toList();

        // 1. What should the panel do next? (opening / new topic / probe / challenge / wrap-up)
        AnswerAnalysis analysis = AnswerAnalysis.NONE;
        TurnPlan turnPlan;
        if (priorQuestions.isEmpty()) {
            turnPlan = TurnDirector.opening();
        } else {
            SessionQuestion last = priorQuestions.get(priorQuestions.size() - 1);
            analysis = parseAnalysis(answerRepository.findByQuestionId(last.getId()).orElse(null));
            List<TurnDirector.Asked> asked = priorQuestions.stream()
                    .map(q -> new TurnDirector.Asked(TurnType.fromDb(q.getTurnType()), q.getTopicId()))
                    .toList();
            turnPlan = TurnDirector.next(plan, asked, analysis);
        }

        // 2. Who is best placed to say it?
        List<String> priorSpeakers = priorQuestions.stream().map(SessionQuestion::getPanelistName).filter(java.util.Objects::nonNull).toList();
        PanelistRosterEntry speaker = SpeakerSelector.select(roster, turnPlan, analysis, priorSpeakers);
        String previousSpeaker = priorSpeakers.isEmpty() ? null : priorSpeakers.get(priorSpeakers.size() - 1);

        // 3. Have them say it, in character.
        try {
            String raw = llmService.generatePanelTurn(
                    session.getRole(), session.getExperienceLevel(), speaker, roster, transcript, candidateSummary,
                    turnPlan, analysis, previousSpeaker);
            com.preppanel.llm.dto.PanelTurnJudgment judgment =
                    objectMapper.readValue(LlmJson.extractObject(raw), com.preppanel.llm.dto.PanelTurnJudgment.class);

            boolean mayTerminate = turnPlan.type() != TurnType.OPENING && turnPlan.type() != TurnType.WRAP_UP;
            if (mayTerminate && Boolean.TRUE.equals(judgment.terminate())) {
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
                    .turnType(turnPlan.type().db())
                    .topicId(turnPlan.topic().id())
                    .build();

            return TurnOutcome.of(questionRepository.save(question));
        } catch (ApiException configProblem) {
            throw configProblem;
        } catch (Exception llmFailure) {
            log.warn("Panel turn generation failed ({}), using a fallback question: {}", turnPlan.type(), llmFailure.getMessage());
            return TurnOutcome.of(questionRepository.save(fallbackQuestion(sessionId, sequenceNum, turnPlan, speaker)));
        }
    }

    /**
     * When the model can't produce a turn: opening and wrap-up have fixed, sensible wording; anything else
     * falls back to the question bank. A bank question isn't tied to a plan topic, so it neither counts as
     * covering one nor as a follow-up.
     */
    private SessionQuestion fallbackQuestion(UUID sessionId, int sequenceNum, TurnPlan turnPlan, PanelistRosterEntry speaker) {
        SessionQuestion fallback;
        switch (turnPlan.type()) {
            case OPENING, WRAP_UP -> fallback = SessionQuestion.builder()
                    .sessionId(sessionId)
                    .sequenceNum(sequenceNum)
                    .questionText(turnPlan.type() == TurnType.OPENING ? OPENING_FALLBACK : WRAP_UP_FALLBACK)
                    .questionType(DEFAULT_QUESTION_TYPE)
                    .source("fallback")
                    .turnType(turnPlan.type().db())
                    .topicId(turnPlan.topic().id())
                    .build();
            default -> {
                fallback = pickFromBank(sessionId, sequenceNum);
                fallback.setTurnType(TurnType.NEW_TOPIC.db());
                fallback.setTopicId("bank-" + sequenceNum);
            }
        }
        // Still attribute it to the speaking panelist so the UI has someone to show.
        fallback.setPanelistArchetypeId(speaker.archetype().id());
        fallback.setPanelistName(speaker.name());
        return fallback;
    }

    // --- interview plan + assessor -----------------------------------------------------

    private InterviewPlan buildPlan(UUID userId, String role, String level, List<PanelistSelection> panelists) {
        int count = "senior".equals(level) || "staff".equals(level) ? TOPICS_FOR_SENIOR_AND_STAFF : TOPICS_FOR_JUNIOR_AND_MID;
        try {
            String raw = llmService.generateInterviewPlan(
                    role, level, profileService.getCandidateSummaryForPrompt(userId), rosterOf(panelists), count);
            InterviewPlan clean = InterviewPlan.sanitize(objectMapper.readValue(LlmJson.extractObject(raw), InterviewPlan.class), count);
            if (clean != null) return clean;
            log.warn("Interview plan from the model was unusable, using the default plan");
        } catch (ApiException configProblem) {
            throw configProblem; // missing/rejected key: tell the user rather than quietly using a generic plan
        } catch (Exception e) {
            log.warn("Interview plan generation failed, using the default plan: {}", e.getMessage());
        }
        return InterviewPlan.defaultPlan(count);
    }

    /** The hidden read on an answer. Never blocks the interview: on any non-config failure it just moves on. */
    private void assessAndStore(UUID userId, InterviewSession session, SessionQuestion current, Answer answer) {
        AnswerAnalysis analysis;
        if (wordCount(answer.getAnswerText()) < MIN_WORDS_WORTH_ASSESSING) {
            analysis = new AnswerAnalysis(null, "specifics", "The answer was too short to say anything concrete.", null, false, false);
        } else {
            analysis = assessWithModel(userId, session, current, answer);
        }
        try {
            answer.setAnalysis(objectMapper.writeValueAsString(analysis));
            answerRepository.save(answer);
        } catch (Exception e) {
            log.warn("Could not store answer analysis: {}", e.getMessage());
        }
    }

    private AnswerAnalysis assessWithModel(UUID userId, InterviewSession session, SessionQuestion current, Answer answer) {
        try {
            InterviewPlan plan = loadPlan(session);
            InterviewPlan.Topic topic = resolveTopic(plan, current.getTopicId());

            // The conversation on this topic so far, ending with the answer being assessed.
            List<TranscriptTurn> topicTurns = new java.util.ArrayList<>();
            for (SessionQuestion q : questionRepository.findBySessionIdOrderBySequenceNumAsc(session.getId())) {
                if (!java.util.Objects.equals(q.getTopicId(), current.getTopicId())) continue;
                String text = q.getId().equals(current.getId())
                        ? answer.getAnswerText()
                        : answerRepository.findByQuestionId(q.getId()).map(Answer::getAnswerText).orElse("");
                topicTurns.add(new TranscriptTurn(label(q), q.getQuestionText(), text, q.getTurnType()));
            }
            if (topicTurns.isEmpty()) {
                topicTurns.add(new TranscriptTurn(label(current), current.getQuestionText(), answer.getAnswerText(), current.getTurnType()));
            }

            String raw = llmService.assessAnswer(
                    session.getRole(), session.getExperienceLevel(), topic, topicTurns,
                    profileService.getCandidateSummaryForPrompt(userId));
            return objectMapper.readValue(LlmJson.extractObject(raw), AnswerAnalysis.class).normalized();
        } catch (ApiException configProblem) {
            throw configProblem;
        } catch (Exception e) {
            log.warn("Answer assessment failed, moving on without follow-up analysis: {}", e.getMessage());
            return AnswerAnalysis.NONE;
        }
    }

    private AnswerAnalysis parseAnalysis(Answer answer) {
        if (answer == null || answer.getAnalysis() == null || answer.getAnalysis().isBlank()) return AnswerAnalysis.NONE;
        try {
            return objectMapper.readValue(answer.getAnalysis(), AnswerAnalysis.class).normalized();
        } catch (Exception e) {
            return AnswerAnalysis.NONE;
        }
    }

    private InterviewPlan loadPlan(InterviewSession session) {
        if (session.getPlan() != null && !session.getPlan().isBlank()) {
            try {
                InterviewPlan plan = objectMapper.readValue(session.getPlan(), InterviewPlan.class);
                if (plan != null && plan.topics() != null && !plan.topics().isEmpty()) return plan;
            } catch (Exception e) {
                log.warn("Could not read stored interview plan, using the default: {}", e.getMessage());
            }
        }
        return InterviewPlan.defaultPlan(TOPICS_FOR_JUNIOR_AND_MID); // sessions created before plans existed
    }

    private InterviewPlan.Topic resolveTopic(InterviewPlan plan, String topicId) {
        if (InterviewPlan.INTRO_ID.equals(topicId)) return InterviewPlan.INTRO;
        if (InterviewPlan.WRAP_ID.equals(topicId)) return InterviewPlan.WRAP;
        InterviewPlan.Topic found = plan.byId(topicId);
        return found != null ? found
                : new InterviewPlan.Topic(String.valueOf(topicId), "General", "ownership", "A specific past example with a clear outcome.", null);
    }

    private String serializePlan(InterviewPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            return null; // loadPlan() falls back to the default plan
        }
    }

    private List<PanelistRosterEntry> rosterOf(List<PanelistSelection> selections) {
        return selections.stream()
                .map(sel -> new PanelistRosterEntry(resolvePanelistName(sel), PanelistArchetype.byId(sel.archetypeId())))
                .toList();
    }

    private TranscriptTurn toTranscriptTurn(SessionQuestion q) {
        Answer ans = answerRepository.findByQuestionId(q.getId()).orElse(null);
        return new TranscriptTurn(label(q), q.getQuestionText(), ans == null ? "" : ans.getAnswerText(), q.getTurnType());
    }

    private String label(SessionQuestion q) {
        return q.getPanelistName() != null ? q.getPanelistName() : "Interviewer";
    }

    private static int wordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
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
                q.getPanelistArchetypeId(), q.getPanelistName(), q.getReactionText(), q.getMoodShift(),
                q.getTurnType()
        );
    }
}
