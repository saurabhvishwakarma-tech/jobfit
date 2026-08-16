-- Phase 2: Resume upload & parsing

CREATE TABLE resumes (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    version_no          INT NOT NULL,
    is_current          BOOLEAN NOT NULL DEFAULT TRUE,
    original_filename   VARCHAR(255) NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    raw_text            TEXT,
    parse_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (parse_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    parse_error         VARCHAR(1000),
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    parsed_at           TIMESTAMPTZ
);

CREATE INDEX idx_resumes_user_id ON resumes (user_id);
-- Only one "current" resume per user at a time.
CREATE UNIQUE INDEX uq_resumes_user_current ON resumes (user_id) WHERE is_current;

CREATE TABLE contact_infos (
    id              BIGSERIAL PRIMARY KEY,
    resume_id       BIGINT NOT NULL UNIQUE REFERENCES resumes (id) ON DELETE CASCADE,
    full_name       VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(50),
    location        VARCHAR(255),
    linkedin_url    VARCHAR(500),
    github_url      VARCHAR(500),
    portfolio_url   VARCHAR(500)
);

CREATE TABLE experiences (
    id              BIGSERIAL PRIMARY KEY,
    resume_id       BIGINT NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    job_title       VARCHAR(255) NOT NULL,
    company         VARCHAR(255) NOT NULL,
    location        VARCHAR(255),
    start_date      DATE,
    end_date        DATE,
    is_current      BOOLEAN NOT NULL DEFAULT FALSE,
    display_order   INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_experiences_resume_id ON experiences (resume_id);

CREATE TABLE experience_highlights (
    id              BIGSERIAL PRIMARY KEY,
    experience_id   BIGINT NOT NULL REFERENCES experiences (id) ON DELETE CASCADE,
    text            VARCHAR(1000) NOT NULL,
    display_order   INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_experience_highlights_experience_id ON experience_highlights (experience_id);

CREATE TABLE educations (
    id              BIGSERIAL PRIMARY KEY,
    resume_id       BIGINT NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    institution     VARCHAR(255) NOT NULL,
    degree          VARCHAR(255),
    field_of_study  VARCHAR(255),
    start_date      DATE,
    end_date        DATE,
    display_order   INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_educations_resume_id ON educations (resume_id);

CREATE TABLE certifications (
    id              BIGSERIAL PRIMARY KEY,
    resume_id       BIGINT NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    issuer          VARCHAR(255),
    issued_date     DATE,
    display_order   INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_certifications_resume_id ON certifications (resume_id);

CREATE TABLE projects (
    id              BIGSERIAL PRIMARY KEY,
    resume_id       BIGINT NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(2000),
    technologies    VARCHAR(500),
    display_order   INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_projects_resume_id ON projects (resume_id);

CREATE TABLE resume_skills (
    id                      BIGSERIAL PRIMARY KEY,
    resume_id               BIGINT NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
    skill_id                BIGINT NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    source                  VARCHAR(20) NOT NULL CHECK (source IN ('EXPLICIT', 'INFERRED')),
    evidence_highlight_id   BIGINT REFERENCES experience_highlights (id) ON DELETE SET NULL,
    confidence              NUMERIC(4, 3),
    UNIQUE (resume_id, skill_id)
);

CREATE INDEX idx_resume_skills_resume_id ON resume_skills (resume_id);
CREATE INDEX idx_resume_skills_skill_id ON resume_skills (skill_id);
