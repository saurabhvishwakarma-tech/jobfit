package com.jobfit.resumeats;

import com.jobfit.common.exception.ResourceNotFoundException;
import com.jobfit.resume.*;
import com.jobfit.resumeats.dto.AtsCheckDto;
import com.jobfit.resumeats.dto.AtsScoreResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.jobfit.resumeats.AtsModels.*;

/**
 * Orchestration for the ATS Compatibility Score: pulls the current resume's
 * already-parsed data plus its raw extracted text and hands derived,
 * plain-value input to the pure AtsScoreAnalyzer. Same
 * low-dependency shape as ResumeQualityService - everything it needs
 * lives in the `resume` module already. Computed fresh on every request
 * rather than persisted.
 */
@Service
public class AtsScoreService {

    /** Anything outside this class is "special" for the noisy-extraction check below. */
    private static final Pattern STANDARD_CHAR_PATTERN =
            Pattern.compile("[A-Za-z0-9\\s.,;:!?'\"()\\-/@%$&+#*]");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\S+");

    private final ResumeRepository resumeRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final ExperienceRepository experienceRepository;
    private final EducationRepository educationRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final AtsScoreAnalyzer analyzer;

    public AtsScoreService(ResumeRepository resumeRepository, ContactInfoRepository contactInfoRepository,
                            ExperienceRepository experienceRepository, EducationRepository educationRepository,
                            ResumeSkillRepository resumeSkillRepository, AtsScoreAnalyzer analyzer) {
        this.resumeRepository = resumeRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.experienceRepository = experienceRepository;
        this.educationRepository = educationRepository;
        this.resumeSkillRepository = resumeSkillRepository;
        this.analyzer = analyzer;
    }

    @Transactional(readOnly = true)
    public AtsScoreResponse analyze(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Resume", resumeId));
        if (resume.getParseStatus() != ParseStatus.READY) {
            throw new IllegalStateException("This resume hasn't finished parsing yet.");
        }

        Optional<ContactInfo> contactInfo = contactInfoRepository.findByResumeId(resumeId);
        boolean hasEmail = contactInfo.map(c -> notBlank(c.getEmail())).orElse(false);
        boolean hasPhone = contactInfo.map(c -> notBlank(c.getPhone())).orElse(false);

        List<Experience> experiences = experienceRepository.findAllByResumeIdOrderByDisplayOrder(resumeId);
        List<ExperienceDateInput> experienceDates = experiences.stream()
                .map(e -> new ExperienceDateInput(e.getStartDate() != null))
                .toList();

        int educationCount = educationRepository.findAllByResumeIdOrderByDisplayOrder(resumeId).size();
        int skillCount = resumeSkillRepository.findAllByResumeId(resumeId).size();

        String rawText = resume.getRawText() == null ? "" : resume.getRawText();
        int wordCount = countWords(rawText);
        double nonStandardRatio = nonStandardCharRatio(rawText);

        AtsInput input = new AtsInput(hasEmail, hasPhone, experiences.size(), educationCount, skillCount,
                wordCount, experienceDates, nonStandardRatio);
        AtsResult result = analyzer.analyze(input);

        List<AtsCheckDto> checkDtos = result.checks().stream()
                .map(c -> new AtsCheckDto(c.label(), c.status().name(), c.detail()))
                .toList();

        return new AtsScoreResponse(resumeId, result.score(), checkDtos);
    }

    private int countWords(String text) {
        if (text.isBlank()) return 0;
        var matcher = WORD_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private double nonStandardCharRatio(String text) {
        if (text.isEmpty()) return 0.0;
        long nonStandard = text.chars()
                .filter(ch -> !STANDARD_CHAR_PATTERN.matcher(Character.toString((char) ch)).matches())
                .count();
        return (double) nonStandard / text.length();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
