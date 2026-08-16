-- Phase 6: Applications tracking

CREATE TABLE applications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id              BIGINT NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    resume_id           BIGINT REFERENCES resumes (id) ON DELETE SET NULL,
    match_analysis_id   BIGINT REFERENCES match_analyses (id) ON DELETE SET NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'SAVED' CHECK (status IN (
        'SAVED', 'APPLIED', 'ONLINE_ASSESSMENT', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN')),
    notes               VARCHAR(4000),
    applied_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- One application per job per user - re-applying to the same posting
    -- should update the existing row, not create a duplicate.
    UNIQUE (user_id, job_id)
);

CREATE INDEX idx_applications_user_id ON applications (user_id);
CREATE INDEX idx_applications_job_id ON applications (job_id);

CREATE TABLE application_status_history (
    id                  BIGSERIAL PRIMARY KEY,
    application_id      BIGINT NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    status              VARCHAR(30) NOT NULL,
    notes               VARCHAR(1000),
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_application_status_history_application_id ON application_status_history (application_id);
