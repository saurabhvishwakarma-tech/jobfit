package com.jobfit.jobparsing;

import java.util.List;

public final class JobExtractionModels {

    private JobExtractionModels() {
    }

    public enum Bucket { REQUIRED, PREFERRED, RESPONSIBILITY, EDUCATION }

    public record RequirementLine(Bucket bucket, String text) {
    }

    public record ExtractionResult(
            List<RequirementLine> lines,
            String experienceYearsSnippet) {
    }
}
