-- Skill taxonomy: created before resume tables since resume_skills references it.
-- Deliberately created early and seeded with common entries so both resume
-- parsing (Phase 2) and job parsing (Phase 3) can normalize against the same
-- dictionary from day one, rather than bolting normalization on later.

CREATE TABLE skills (
    id              BIGSERIAL PRIMARY KEY,
    canonical_name  VARCHAR(150) NOT NULL UNIQUE,
    category        VARCHAR(30) NOT NULL
        CHECK (category IN ('LANGUAGE', 'FRAMEWORK', 'TOOL', 'DOMAIN', 'SOFT')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE skill_aliases (
    id          BIGSERIAL PRIMARY KEY,
    alias       VARCHAR(150) NOT NULL UNIQUE,
    skill_id    BIGINT NOT NULL REFERENCES skills (id) ON DELETE CASCADE
);

CREATE INDEX idx_skill_aliases_skill_id ON skill_aliases (skill_id);

-- Seed a starter dictionary. This is intentionally small - it grows over time
-- as unmatched terms are reviewed (see docs/JobFit_Design_v1.md, Matching
-- Algorithm section, step 1-2). Canonical names use their most common
-- public spelling.
INSERT INTO skills (canonical_name, category) VALUES
    ('Java', 'LANGUAGE'),
    ('JavaScript', 'LANGUAGE'),
    ('TypeScript', 'LANGUAGE'),
    ('Python', 'LANGUAGE'),
    ('SQL', 'LANGUAGE'),
    ('C#', 'LANGUAGE'),
    ('Go', 'LANGUAGE'),
    ('Spring Boot', 'FRAMEWORK'),
    ('Spring Security', 'FRAMEWORK'),
    ('React', 'FRAMEWORK'),
    ('Angular', 'FRAMEWORK'),
    ('Vue.js', 'FRAMEWORK'),
    ('Node.js', 'FRAMEWORK'),
    ('Hibernate', 'FRAMEWORK'),
    ('Express', 'FRAMEWORK'),
    ('Django', 'FRAMEWORK'),
    ('.NET', 'FRAMEWORK'),
    ('PostgreSQL', 'TOOL'),
    ('MySQL', 'TOOL'),
    ('MongoDB', 'TOOL'),
    ('Redis', 'TOOL'),
    ('Docker', 'TOOL'),
    ('Kubernetes', 'TOOL'),
    ('AWS', 'TOOL'),
    ('Azure', 'TOOL'),
    ('Google Cloud Platform', 'TOOL'),
    ('Git', 'TOOL'),
    ('Jenkins', 'TOOL'),
    ('GitHub Actions', 'TOOL'),
    ('Terraform', 'TOOL'),
    ('Kafka', 'TOOL'),
    ('REST APIs', 'DOMAIN'),
    ('GraphQL', 'DOMAIN'),
    ('Microservices', 'DOMAIN'),
    ('CI/CD', 'DOMAIN'),
    ('Agile', 'DOMAIN'),
    ('Test-Driven Development', 'DOMAIN'),
    ('Communication', 'SOFT'),
    ('Leadership', 'SOFT'),
    ('Teamwork', 'SOFT'),
    ('Problem Solving', 'SOFT');

INSERT INTO skill_aliases (alias, skill_id)
SELECT alias, s.id FROM (VALUES
    ('JS', 'JavaScript'),
    ('TS', 'TypeScript'),
    ('Postgres', 'PostgreSQL'),
    ('K8s', 'Kubernetes'),
    ('GCP', 'Google Cloud Platform'),
    ('Spring', 'Spring Boot'),
    ('VueJS', 'Vue.js'),
    ('Vue', 'Vue.js'),
    ('NodeJS', 'Node.js'),
    ('Node', 'Node.js'),
    ('Dotnet', '.NET'),
    ('ASP.NET', '.NET'),
    ('REST', 'REST APIs'),
    ('RESTful APIs', 'REST APIs'),
    ('TDD', 'Test-Driven Development')
) AS aliases(alias, canonical_name)
JOIN skills s ON s.canonical_name = aliases.canonical_name;
