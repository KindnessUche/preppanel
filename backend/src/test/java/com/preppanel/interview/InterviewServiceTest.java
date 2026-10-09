package com.preppanel.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preppanel.exception.ApiException;
import com.preppanel.interview.dto.CreateSessionRequest;
import com.preppanel.interview.dto.PanelistSelection;
import com.preppanel.interview.dto.SessionResponse;
import com.preppanel.interview.dto.SubmitAnswerRequest;
import com.preppanel.llm.LlmService;
import com.preppanel.profile.ProfileService;
import com.preppanel.question.QuestionBank;
import com.preppanel.question.QuestionBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionQuestionRepository questionRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private LlmService llmService;
    @Mock
    private ProfileService profileService;

    private InterviewService interviewService;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        interviewService = new InterviewService(
                sessionRepository, questionRepository, answerRepository, questionBankRepository,
                llmService, profileService, new ObjectMapper());
    }

    private InterviewSession sessionOwnedBy(UUID owner, String panelistsJson) {
        return InterviewSession.builder()
                .id(sessionId).userId(owner).role("Backend Engineer").tone("neutral")
                .mode("text").status("in_progress").panelists(panelistsJson)
                .build();
    }

    private QuestionBank bankQuestion() {
        return QuestionBank.builder()
                .id(UUID.randomUUID()).questionText("Tell me about yourself.")
                .questionType("behavioral").active(true).build();
    }

    // --- legacy (no panel) path -------------------------------------------------

    @Test
    void createSession_legacyPath_llmFailure_fallsBackToBankQuestion() {
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of());
        when(llmService.generateBehavioralQuestion(anyString(), anyList(), anyString(), any()))
                .thenThrow(new RuntimeException("Groq is down"));
        when(questionBankRepository.findRandomUnusedForSession(eq(sessionId), anyString()))
                .thenReturn(bankQuestion());
        when(questionRepository.save(any(SessionQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        // no panelists -> legacy single-tone path
        SessionResponse response = interviewService.createSession(userId,
                new CreateSessionRequest("Backend Engineer", "text", "neutral", List.of(), null));

        assertThat(response.currentQuestion().questionText()).isEqualTo("Tell me about yourself.");
        assertThat(response.completed()).isFalse();

        ArgumentCaptor<SessionQuestion> captor = ArgumentCaptor.forClass(SessionQuestion.class);
        verify(questionRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("bank");
    }

    @Test
    void createSession_bankExhausted_throws500NotSilentFailure() {
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of());
        when(llmService.generateBehavioralQuestion(anyString(), anyList(), anyString(), any()))
                .thenThrow(new RuntimeException("Groq is down"));
        when(questionBankRepository.findRandomUnusedForSession(eq(sessionId), anyString())).thenReturn(null);

        assertThatThrownBy(() -> interviewService.createSession(userId,
                new CreateSessionRequest("Backend Engineer", "text", "neutral", List.of(), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exhausted");
    }

    // --- panel path --------------------------------------------------------------

    @Test
    void createSession_panelPath_llmFailure_fallsBackToBankQuestion_attributedToSpeaker() {
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of());
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any()))
                .thenThrow(new RuntimeException("Gemini timeout"));
        when(questionBankRepository.findRandomUnusedForSession(eq(sessionId), anyString()))
                .thenReturn(bankQuestion());
        when(questionRepository.save(any(SessionQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<PanelistSelection> panelists = List.of(new PanelistSelection("tech-lead", null));
        SessionResponse response = interviewService.createSession(userId,
                new CreateSessionRequest("Backend Engineer", "text", null, panelists, "mid"));

        assertThat(response.currentQuestion().questionText()).isEqualTo("Tell me about yourself.");
        assertThat(response.currentQuestion().panelistArchetypeId()).isEqualTo("tech-lead");
        assertThat(response.currentQuestion().panelistName()).isEqualTo("The Tech Lead");
    }

    @Test
    void createSession_rejectsMoreThanFourPanelists() {
        List<PanelistSelection> tooMany = List.of(
                new PanelistSelection("hiring-manager", null),
                new PanelistSelection("tech-lead", null),
                new PanelistSelection("skeptic", null),
                new PanelistSelection("bar-raiser", null),
                new PanelistSelection("peer", null)
        );

        assertThatThrownBy(() -> interviewService.createSession(userId,
                new CreateSessionRequest("Backend Engineer", "text", null, tooMany, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at most 4");

        verifyNoInteractions(sessionRepository, llmService);
    }

    @Test
    void createSession_panelTerminatesOnFirstTurn_completesSessionImmediately() throws Exception {
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of());
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any()))
                .thenReturn("{\"reaction\":\"\",\"moodShift\":\"negative\",\"nextQuestion\":\"\","
                        + "\"terminate\":true,\"terminateReason\":\"Sustained hostility detected.\"}");

        List<PanelistSelection> panelists = List.of(new PanelistSelection("skeptic", null));
        SessionResponse response = interviewService.createSession(userId,
                new CreateSessionRequest("Backend Engineer", "text", null, panelists, null));

        assertThat(response.completed()).isTrue();
        assertThat(response.terminatedByPanel()).isTrue();
        assertThat(response.terminationReason()).isEqualTo("Sustained hostility detected.");
        assertThat(response.currentQuestion()).isNull();

        ArgumentCaptor<InterviewSession> captor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionRepository, times(2)).save(captor.capture()); // initial create + completion update
        assertThat(captor.getValue().getStatus()).isEqualTo("completed");
    }

    // --- submitAnswer: ownership + idempotency ------------------------------------

    @Test
    void submitAnswer_crossUserSession_throws404NotFound() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(sessionOwnedBy(otherUserId, "[]")));

        assertThatThrownBy(() -> interviewService.submitAnswer(userId, sessionId,
                new SubmitAnswerRequest("My answer")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));

        verifyNoInteractions(answerRepository, llmService);
    }

    @Test
    void submitAnswer_rejectsAnsweringTheSameQuestionTwice() {
        InterviewSession session = sessionOwnedBy(userId, "[]");
        SessionQuestion currentQuestion = SessionQuestion.builder()
                .id(UUID.randomUUID()).sessionId(sessionId).sequenceNum(1)
                .questionText("Q1").questionType("behavioral").source("bank").build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.countBySessionId(sessionId)).thenReturn(1);
        when(questionRepository.findBySessionIdAndSequenceNum(sessionId, 1)).thenReturn(Optional.of(currentQuestion));
        when(answerRepository.findByQuestionId(currentQuestion.getId()))
                .thenReturn(Optional.of(Answer.builder().id(UUID.randomUUID())
                        .questionId(currentQuestion.getId()).answerText("already answered").build()));

        assertThatThrownBy(() -> interviewService.submitAnswer(userId, sessionId,
                new SubmitAnswerRequest("Trying again")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already been answered");

        verify(answerRepository, never()).save(any());
    }

    @Test
    void submitAnswer_rejectsOnCompletedSession() {
        InterviewSession session = sessionOwnedBy(userId, "[]");
        session.setStatus("completed");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewService.submitAnswer(userId, sessionId,
                new SubmitAnswerRequest("Too late")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already completed");

        verifyNoInteractions(answerRepository);
    }
}
