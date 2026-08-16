-- Phase 4: Matching & scoring engine

CREATE TABLE match_analyses (
    id                  BIGSERIAL PRIMARY KEY,
    resume_id           BIGINT NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    job_id              BIGINT NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    overall_score       INT NOT NULL,
    recommendation      VARCHAR(30) NOT NULL,
    recommendation_reason VARCHAR(2000) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_match_analyses_resume_id ON match_analyses (resume_id);
CREATE INDEX idx_match_analyses_job_id ON match_analyses (job_id);

CREATE TABLE score_components (
    id                  BIGSERIAL PRIMARY KEY,
    match_analysis_id   BIGINT NOT NULL REFERENCES match_analyses (id) ON DELETE CASCADE,
    category            VARCHAR(60) NOT NULL,
    max_points          NUMERIC(5, 2) NOT NULL,
    earned_points       NUMERIC(5, 2) NOT NULL,
    explanation         VARCHAR(500) NOT NULL,
    display_order       INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_score_components_analysis_id ON score_components (match_analysis_id);

CREATE TABLE evidence (
    id                  BIGSERIAL PRIMARY KEY,
    match_analysis_id   BIGINT NOT NULL REFERENCES match_analyses (id) ON DELETE CASCADE,
    job_requirement_id  BIGINT NOT NULL REFERENCES job_requirements (id) ON DELETE CASCADE,
    match_type          VARCHAR(20) NOT NULL CHECK (match_type IN ('EXPLICIT', 'INFERRED', 'ABSENT')),
    strength            VARCHAR(20) NOT NULL CHECK (strength IN ('STRONG', 'PARTIAL', 'MISSING')),
    resume_ref_type     VARCHAR(30),
    resume_ref_id       BIGINT,
    explanation_text    VARCHAR(500) NOT NULL,
    confidence          NUMERIC(4, 3)
);

CREATE INDEX idx_evidence_analysis_id ON evidence (match_analysis_id);
CREATE INDEX idx_evidence_requirement_id ON evidence (job_requirement_id);
