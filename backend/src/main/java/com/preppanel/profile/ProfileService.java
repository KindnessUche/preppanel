package com.preppanel.profile;

import com.preppanel.exception.ApiException;
import com.preppanel.profile.dto.ProfileResponse;
import com.preppanel.profile.dto.UpdatePortfolioRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Resume/portfolio ingestion - the biggest feature gap flagged in PRD v3
 * (section 7). Deliberately simple for v1: PDFBox for text extraction and a
 * fixed-vocabulary keyword scan for skills, rather than an extra LLM call per
 * upload (keeps this endpoint free and instant; the free-tier LLM budget is
 * spent on interview turns/scoring instead, where it actually matters).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileService {

    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024; // 5MB
    /** Cap what actually reaches the LLM prompt, independent of how much resume text we store. */
    private static final int MAX_RESUME_CHARS_FOR_PROMPT = 6000;

    // Small fixed vocabulary rather than an LLM/NLP pass - cheap, deterministic, and
    // good enough to flavor interview questions. Expand this list over time.
    private static final List<String> SKILL_VOCABULARY = List.of(
            "Java", "Spring Boot", "Spring", "Kotlin", "Python", "Django", "Flask",
            "JavaScript", "TypeScript", "React", "Next.js", "Vue", "Angular", "Node.js",
            "Go", "Rust", "C++", "C#", ".NET", "Ruby", "Rails", "PHP", "Swift",
            "PostgreSQL", "MySQL", "MongoDB", "Redis", "SQL", "GraphQL", "REST",
            "Docker", "Kubernetes", "AWS", "GCP", "Azure", "Terraform", "CI/CD",
            "Kafka", "RabbitMQ", "Microservices", "gRPC", "Machine Learning",
            "TensorFlow", "PyTorch", "Data Engineering", "Spark", "Airflow"
    );

    private final CandidateProfileRepository profileRepository;

    @Transactional
    public ProfileResponse uploadResume(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Resume must be 5MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Resume must be a PDF file");
        }

        String resumeText;
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Password-protected PDFs aren't supported");
            }
            resumeText = new PDFTextStripper().getText(document);
        } catch (IOException e) {
            log.warn("Failed to parse uploaded resume for user {}: {}", userId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read that PDF - is it valid and not password-protected?");
        }

        resumeText = resumeText == null ? "" : resumeText.trim();
        if (resumeText.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "No extractable text found in that PDF (it may be a scanned image) - a text-based PDF is needed");
        }

        List<String> skills = extractSkills(resumeText);

        CandidateProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> CandidateProfile.builder().userId(userId).build());

        profile.setResumeText(resumeText);
        profile.setResumeFilename(sanitizeFilename(file.getOriginalFilename()));
        profile.setParsedSkills(skills);

        profile = profileRepository.save(profile);
        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse updatePortfolio(UUID userId, UpdatePortfolioRequest request) {
        CandidateProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> CandidateProfile.builder().userId(userId).build());

        String githubUsername = request.githubUsername() == null || request.githubUsername().isBlank()
                ? null : request.githubUsername().trim();
        profile.setGithubUsername(githubUsername);
        profile.setPortfolioUrls(request.portfolioUrls() == null ? List.of() : request.portfolioUrls());

        profile = profileRepository.save(profile);
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return profileRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> new ProfileResponse(false, null, List.of(), null, List.of(), null));
    }

    /**
     * Short candidate-background blurb for prompt injection (InterviewService),
     * capped well below the full resume text to keep token cost bounded per the
     * v3 critique's free-tier-exhaustion concern. Returns "" if no profile exists
     * yet - callers should treat that as "no background available", not an error.
     */
    @Transactional(readOnly = true)
    public String getCandidateSummaryForPrompt(UUID userId) {
        return profileRepository.findByUserId(userId)
                .map(profile -> {
                    StringBuilder sb = new StringBuilder();
                    if (profile.getParsedSkills() != null && !profile.getParsedSkills().isEmpty()) {
                        sb.append("Skills mentioned in their resume: ")
                                .append(String.join(", ", profile.getParsedSkills()))
                                .append(".\n");
                    }
                    if (profile.getGithubUsername() != null && !profile.getGithubUsername().isBlank()) {
                        sb.append("GitHub: ").append(profile.getGithubUsername()).append(".\n");
                    }
                    if (profile.getResumeText() != null && !profile.getResumeText().isBlank()) {
                        String truncated = profile.getResumeText().length() > MAX_RESUME_CHARS_FOR_PROMPT
                                ? profile.getResumeText().substring(0, MAX_RESUME_CHARS_FOR_PROMPT) + "..."
                                : profile.getResumeText();
                        sb.append("Resume excerpt:\n").append(truncated);
                    }
                    return sb.toString();
                })
                .orElse("");
    }

    private List<String> extractSkills(String resumeText) {
        String haystack = resumeText.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String skill : SKILL_VOCABULARY) {
            if (haystack.contains(skill.toLowerCase(Locale.ROOT))) {
                found.add(skill);
            }
        }
        return found;
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) return "resume.pdf";
        // Strip any path components a malicious client might sneak in, keep just the name.
        String name = original.replaceAll("^.*[/\\\\]", "");
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private ProfileResponse toResponse(CandidateProfile profile) {
        return new ProfileResponse(
                profile.getResumeText() != null && !profile.getResumeText().isBlank(),
                profile.getResumeFilename(),
                profile.getParsedSkills() == null ? List.of() : profile.getParsedSkills(),
                profile.getGithubUsername(),
                profile.getPortfolioUrls() == null ? List.of() : profile.getPortfolioUrls(),
                profile.getUpdatedAt()
        );
    }
}
