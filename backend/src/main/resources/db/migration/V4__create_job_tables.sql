-- Phase 3: Job input & JD parsing

CREATE TABLE jobs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title               VARCHAR(255) NOT NULL,
    company             VARCHAR(255),
    raw_description     TEXT NOT NULL,
    source_url          VARCHAR(1000),
    parse_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (parse_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    parse_error         VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    parsed_at           TIMESTAMPTZ
);

CREATE INDEX idx_jobs_user_id ON jobs (user_id);

CREATE TABLE job_requirements (
    id                  BIGSERIAL PRIMARY KEY,
    job_id              BIGINT NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    type                VARCHAR(20) NOT NULL CHECK (type IN (
        'REQUIRED_SKILL', 'PREFERRED_SKILL', 'EXPERIENCE_YEARS', 'EDUCATION',
        'RESPONSIBILITY', 'DOMAIN', 'SOFT_SKILL')),
    raw_text            VARCHAR(1000) NOT NULL,
    normalized_skill_id BIGINT REFERENCES skills (id) ON DELETE SET NULL,
    weight              NUMERIC(4, 3),
    display_order       INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_job_requirements_job_id ON job_requirements (job_id);
CREATE INDEX idx_job_requirements_skill_id ON job_requirements (normalized_skill_id);
