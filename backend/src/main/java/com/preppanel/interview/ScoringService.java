package com.preppanel.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preppanel.exception.ApiException;
import com.preppanel.interview.dto.ScoreResponse;
import com.preppanel.interview.dto.SessionReportResponse;
import com.preppanel.llm.LlmService;
import com.preppanel.llm.dto.ScoreJudgment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScoringService {

    private final InterviewSessionRepository sessionRepository;
    private final SessionQuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ScoreRepository scoreRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    /**
     * Scores every answered-but-unscored question in a session. Safe to call multiple
     * times - already-scored answers are skipped, so this endpoint is idempotent.
     */
    @Transactional
    public SessionReportResponse scoreSession(UUID userId, UUID sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        List<SessionQuestion> questions = questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);

        for (SessionQuestion question : questions) {
            Answer answer = answerRepository.findByQuestionId(question.getId()).orElse(null);
            if (answer == null) {
                continue; // not answered yet - nothing to score
            }
            if (scoreRepository.findByAnswerId(answer.getId()).isPresent()) {
                continue; // already scored - idempotent skip
            }
            scoreOneAnswer(session.getRole(), question, answer);
        }

        return buildReport(session);
    }

    @Transactional(readOnly = true)
    public SessionReportResponse getReport(UUID userId, UUID sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        return buildReport(session);
    }

    private void scoreOneAnswer(String role, SessionQuestion question, Answer answer) {
        ScoreJudgment judgment = judgeWithOneRetry(role, question.getQuestionText(), answer.getAnswerText());

        Score score = Score.builder()
                .answerId(answer.getId())
                .contentScore(clamp(judgment.contentScore()))
                .structureScore(clamp(judgment.structureScore()))
                .technicalScore(judgment.technicalScore() == null ? null : clamp(judgment.technicalScore()))
                .feedbackText(judgment.feedbackText() == null || judgment.feedbackText().isBlank()
                        ? "No specific feedback generated."
                        : judgment.feedbackText())
                .build();

        scoreRepository.save(score);
    }

    /** LLM output isn't trustworthy by construction - parse defensively, retry once on garbage. */
    private ScoreJudgment judgeWithOneRetry(String role, String questionText, String answerText) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String raw = llmService.scoreAnswer(role, questionText, answerText);
                String cleaned = stripMarkdownFences(raw);
                ScoreJudgment judgment = objectMapper.readValue(cleaned, ScoreJudgment.class);

                if (judgment.contentScore() == null || judgment.structureScore() == null) {
                    throw new IllegalArgumentException("Missing required score fields");
                }
                return judgment;
            } catch (Exception e) {
                log.warn("Scoring attempt {} failed to parse LLM output: {}", attempt, e.getMessage());
                if (attempt == 2) {
                    // Both attempts failed - fall back to a neutral, clearly-flagged score
                    // rather than blocking the whole report.
                    return new ScoreJudgment(50, 50, null,
                            "Automated scoring was unavailable for this answer - please review manually.");
                }
            }
        }
        // Unreachable, but keeps the compiler happy.
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Scoring failed unexpectedly");
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

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private SessionReportResponse buildReport(InterviewSession session) {
        List<SessionQuestion> questions = questionRepository.findBySessionIdOrderBySequenceNumAsc(session.getId());

        List<SessionReportResponse.Item> items = questions.stream()
                .map(q -> {
                    Answer answer = answerRepository.findByQuestionId(q.getId()).orElse(null);
                    ScoreResponse scoreResponse = null;

                    if (answer != null) {
                        Score score = scoreRepository.findByAnswerId(answer.getId()).orElse(null);
                        if (score != null) {
                            scoreResponse = new ScoreResponse(
                                    score.getContentScore(),
                                    score.getStructureScore(),
                                    score.getTechnicalScore(),
                                    score.getDeliveryScore(),
                                    score.getFeedbackText()
                            );
                        }
                    }

                    return new SessionReportResponse.Item(
                            q.getSequenceNum(),
                            q.getQuestionText(),
                            q.getPanelistName(),
                            answer == null ? null : answer.getAnswerText(),
                            scoreResponse
                    );
                })
                .toList();

        Double avgContent = items.stream()
                .map(SessionReportResponse.Item::score)
                .filter(java.util.Objects::nonNull)
                .mapToInt(ScoreResponse::contentScore)
                .average()
                .stream().boxed().findFirst().orElse(null);

        Double avgStructure = items.stream()
                .map(SessionReportResponse.Item::score)
                .filter(java.util.Objects::nonNull)
                .mapToInt(ScoreResponse::structureScore)
                .average()
                .stream().boxed().findFirst().orElse(null);

        return new SessionReportResponse(
                session.getId(), session.getRole(), session.getTone(), session.getStatus(),
                avgContent, avgStructure, items
        );
    }

    private InterviewSession getOwnedSession(UUID userId, UUID sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Session not found");
        }

        return session;
    }
}
