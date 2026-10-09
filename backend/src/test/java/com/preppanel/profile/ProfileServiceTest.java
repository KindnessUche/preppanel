package com.preppanel.profile;

import com.preppanel.profile.dto.ProfileResponse;
import com.preppanel.profile.dto.UpdatePortfolioRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private CandidateProfileRepository profileRepository;

    private ProfileService profileService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(profileRepository);
    }

    @Test
    void getProfile_noProfileYet_returnsEmptyDefaultRatherThanThrowing() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        ProfileResponse response = profileService.getProfile(userId);

        assertThat(response.hasResume()).isFalse();
        assertThat(response.parsedSkills()).isEmpty();
        assertThat(response.portfolioUrls()).isEmpty();
        assertThat(response.githubUsername()).isNull();
    }

    @Test
    void getCandidateSummaryForPrompt_noProfile_returnsEmptyString() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        assertThat(profileService.getCandidateSummaryForPrompt(userId)).isEmpty();
    }

    @Test
    void getCandidateSummaryForPrompt_includesSkillsGithubAndResumeExcerpt() {
        CandidateProfile profile = CandidateProfile.builder()
                .userId(userId)
                .resumeText("Built scalable systems with Java and Spring Boot.")
                .githubUsername("octocat")
                .parsedSkills(List.of("Java", "Spring Boot"))
                .build();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        String summary = profileService.getCandidateSummaryForPrompt(userId);

        assertThat(summary).contains("Java, Spring Boot");
        assertThat(summary).contains("octocat");
        assertThat(summary).contains("Built scalable systems");
    }

    @Test
    void getCandidateSummaryForPrompt_truncatesVeryLongResumeText() {
        String longText = "x".repeat(10_000);
        CandidateProfile profile = CandidateProfile.builder()
                .userId(userId).resumeText(longText).build();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        String summary = profileService.getCandidateSummaryForPrompt(userId);

        // Capped well under the full 10,000 chars to bound prompt/token cost.
        assertThat(summary.length()).isLessThan(7000);
        assertThat(summary).endsWith("...");
    }

    @Test
    void updatePortfolio_createsProfileIfNoneExists_andBlankGithubClearsIt() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(profileRepository.save(any(CandidateProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updatePortfolio(userId,
                new UpdatePortfolioRequest("  ", List.of("https://example.com/me")));

        ArgumentCaptor<CandidateProfile> captor = ArgumentCaptor.forClass(CandidateProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getGithubUsername()).isNull(); // blank -> cleared
        assertThat(captor.getValue().getPortfolioUrls()).containsExactly("https://example.com/me");
        assertThat(response.portfolioUrls()).containsExactly("https://example.com/me");
    }

    @Test
    void updatePortfolio_trimsGithubUsername() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(profileRepository.save(any(CandidateProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        profileService.updatePortfolio(userId, new UpdatePortfolioRequest("  octocat  ", null));

        ArgumentCaptor<CandidateProfile> captor = ArgumentCaptor.forClass(CandidateProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getGithubUsername()).isEqualTo("octocat");
        assertThat(captor.getValue().getPortfolioUrls()).isEmpty();
    }
}
