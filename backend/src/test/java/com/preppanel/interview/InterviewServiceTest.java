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
    void createSession_panelPath_llmFailure_usesFixedOpening_attributedToSpeaker() {
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of());
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Gemini timeout"));
        when(questionRepository.save(any(SessionQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<PanelistSelection> panelists = List.of(new PanelistSelection("tech-lead", null));
        SessionResponse response = interviewService.createSession(userId,
                new CreateSessionRequest("Backend Engineer", "text", null, panelists, "mid"));

        // The opening has fixed wording, so a model failure never leaves the candidate with a random bank question.
        assertThat(response.currentQuestion().questionText()).contains("walk us through your background");
        assertThat(response.currentQuestion().turnType()).isEqualTo("opening");
        assertThat(response.currentQuestion().panelistArchetypeId()).isEqualTo("tech-lead");
        assertThat(response.currentQuestion().panelistName()).isEqualTo("The Tech Lead");
    }

    @Test
    void createSession_panelPath_savesAPlan_evenWhenPlanGenerationFails() {
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of());
        when(llmService.generateInterviewPlan(anyString(), any(), any(), anyList(), anyInt()))
                .thenReturn("this is not json");
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn("{\"reaction\":\"Hi, I'm Sam.\",\"moodShift\":\"neutral\",\"nextQuestion\":\"Walk me through your background?\",\"terminate\":false}");
        when(questionRepository.save(any(SessionQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        interviewService.createSession(userId, new CreateSessionRequest("Backend Engineer", "text", null,
                List.of(new PanelistSelection("hiring-manager", null)), "senior"));

        ArgumentCaptor<InterviewSession> captor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getPlan()).contains("\"topics\"").contains("\"t5\""); // senior => 5 default topics
    }

    @Test
    void createSession_panelPath_usesTheModelsPlan_whenItIsValid() {
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });
        when(questionRepository.findBySessionIdOrderBySequenceNumAsc(sessionId)).thenReturn(List.of());
        when(llmService.generateInterviewPlan(anyString(), any(), any(), anyList(), anyInt())).thenReturn(
                "```json\n{\"topics\":[{\"label\":\"Owning an outage\",\"lens\":\"ownership\",\"focus\":\"x\"},"
                        + "{\"label\":\"Tough review\",\"lens\":\"collaboration\",\"focus\":\"y\"},"
                        + "{\"label\":\"A design call\",\"lens\":\"technical\",\"focus\":\"z\"},"
                        + "{\"label\":\"A miss\",\"lens\":\"growth\",\"focus\":\"w\"}]}\n```");
        when(llmService.generatePanelTurn(anyString(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn("{\"reaction\":\"\",\"moodShift\":\"neutral\",\"nextQuestion\":\"Walk me through it?\",\"terminate\":false}");
        when(questionRepository.save(any(SessionQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        interviewService.createSession(userId, new CreateSessionRequest("Backend Engineer", "text", null,
                List.of(new PanelistSelection("hiring-manager", null)), "mid"));

        ArgumentCaptor<InterviewSession> captor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getPlan()).contains("Owning an outage").contains("\"t4\"");
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
