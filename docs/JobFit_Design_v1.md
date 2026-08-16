# JobFit — Design Document v1

## 1. Refined Product Definition

JobFit is an evidence-based job-matching and application-tracking tool for a job seeker's own search. The product's core claim is **explainability**: every score, match, and recommendation traces back to a specific resume sentence and a specific job requirement, through a deterministic scoring engine. AI is used for interpretation and language generation, never as the sole arbiter of fit. Secondary but real value: a standalone resume-quality linter, and an applications CRM with fit-score history.

This framing is what separates it from the hundreds of "upload resume, get AI score" clones. Lead with that in the README and in interviews.

## 2. MVP Scope

- Auth: register/login, JWT access + refresh.
- Resume upload (PDF only) → text extraction → structured data → **user reviews/edits the parse before it's used** (critical trust mechanism, see §14).
- Job input via pasted text → structured requirements.
- Deterministic matching + scoring engine with full breakdown.
- Evidence view: requirement → resume evidence, tagged Explicit / Inferred / Absent.
- Should-I-Apply recommendation with rule-based rationale.
- Standalone Resume Quality Analysis.
- Applications tracking (statuses + history).
- Dashboard with core aggregates.
- Job comparison (2–4 jobs side by side).

## 3. Postponed (Post-MVP)

Job-URL scraping/import (Indeed/LinkedIn scraping has ToS and legal exposure — start with paste-only; a "fetch text from a URL I provide" helper is a safe, small post-MVP add), DOCX support, multiple concurrent tailored resumes per job, browser extension, LinkedIn import, email/notification reminders, tailored-resume auto-generation/export, trend-over-time analytics charts, admin/multi-tenant features, billing, mobile app, custom-trained NER model, org/team collaboration.

Everything above is deferred because it adds surface area without adding proof of engineering skill. Resist adding these until the MVP is fully solid and tested.

## 4. System Architecture

**Modular monolith**, package-by-module (not package-by-layer), single Spring Boot deployable:

```
com.jobfit
 ├─ user            (auth, profile)
 ├─ resume          (upload, storage, versions)
 ├─ resumeparsing   (PDF extraction → structured resume)
 ├─ job             (job CRUD)
 ├─ jobparsing      (JD text → structured requirements)
 ├─ matching        (evidence alignment engine)
 ├─ scoring         (deterministic score computation)
 ├─ application     (tracking + status lifecycle)
 ├─ analytics       (dashboard aggregation)
 ├─ ai              (LLM client abstraction, adapters, prompts)
 └─ common          (security, exceptions, validation, audit)
```

Rules: each module owns its own tables/repositories; other modules only call it through a public service interface, never its repository directly. This is enforced by package-private repository classes. It gives you the enforced separation of microservices without the deployment/network complexity — and it's a very defensible architecture decision in interviews ("why not microservices?" → you can answer that with confidence).

Frontend: React + TypeScript SPA, separate deployable, talks to the API over REST.

**No message broker.** Resume/JD parsing calls an LLM and can take a few seconds — handle this with `@Async` + a `ParseJob` status row (`PENDING/PROCESSING/DONE/FAILED`) that the frontend polls or subscribes to via Server-Sent Events. This demonstrates async task handling without pulling in Kafka for a single-user-at-a-time portfolio app.

Single Postgres database for all modules (schema-per-module optional, not necessary at this scale).

## 5. Modules

Covered above — same list, each with controller → service → repository → DTO internally.

## 6. Database Design

A few deliberate departures from your entity list, explained inline.

```
users
  id, email (unique), password_hash, full_name, created_at

resumes                          -- immutable, versioned uploads
  id, user_id, version_no, is_current, storage_key,
  raw_text, parse_status, uploaded_at

  Note: I dropped the separate ResumeVersion table. Each parsed upload
  is itself a version row (is_current flag marks the active one).
  Two tables for the same lifecycle would be redundant at this scale.
  Applications reference a specific resume_id, so the exact version
  used for a given application/analysis is always preserved — that's
  the actual reason ResumeVersion existed in your sketch, and this
  design still gives you it.

contact_infos      (1:1 resumes)  -- name, email, phone, location, links(json)
experiences        (N:1 resumes)  -- title, company, start_date, end_date, order
experience_highlights (N:1 experiences) -- one row per bullet point
                                     (needed for bullet-level evidence linking)
educations         (N:1 resumes)  -- institution, degree, field, dates
certifications     (N:1 resumes)  -- name, issuer, date
projects           (N:1 resumes)  -- name, description, tech(json)

skills                            -- global taxonomy
  id, canonical_name, category(LANGUAGE/FRAMEWORK/TOOL/DOMAIN/SOFT)
skill_aliases                     -- e.g. "JS" -> "JavaScript" skill_id
resume_skills      (N:N resumes<->skills)
  resume_id, skill_id, source(EXPLICIT/INFERRED), evidence_highlight_id (nullable)

jobs
  id, user_id, title, company, raw_description, source_url(nullable), created_at

job_requirements    (N:1 jobs)
  id, job_id, type(REQUIRED_SKILL/PREFERRED_SKILL/EXPERIENCE_YEARS/
       EDUCATION/RESPONSIBILITY/DOMAIN/SOFT_SKILL),
  raw_text, normalized_skill_id(nullable), weight

match_analyses      (N:1 resumes, N:1 jobs)
  id, resume_id, job_id, overall_score, recommendation, created_at

score_components    (N:1 match_analyses)
  id, match_analysis_id, category, max_points, earned_points, explanation

evidence            (N:1 match_analyses, N:1 job_requirements)
  id, match_analysis_id, job_requirement_id,
  match_type(EXPLICIT/INFERRED/ABSENT), strength(STRONG/PARTIAL/MISSING),
  resume_ref_type(EXPERIENCE_HIGHLIGHT/SKILL/EDUCATION/nullable),
  resume_ref_id(nullable), explanation_text, confidence(nullable)

applications        (N:1 jobs, N:1 resumes, N:1 match_analyses nullable)
  id, user_id, status, applied_at, updated_at, notes

application_status_history (N:1 applications)
  id, application_id, status, changed_at
```

This is 16 tables — reasonably normalized, no God table, no unnecessary joins-of-joins. `experience_highlights` is the one addition beyond your list, and it's what actually makes "Requirement → Resume Evidence" clickable/traceable rather than a vague paragraph reference.

## 7. API Design (representative)

```
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/refresh

POST   /api/resumes                    (multipart upload, 202 Accepted, returns parse job id)
GET    /api/resumes
GET    /api/resumes/{id}
PATCH  /api/resumes/{id}                (user corrections to parsed fields)
DELETE /api/resumes/{id}
GET    /api/resumes/{id}/quality        (Resume Quality Analysis)
GET    /api/parse-jobs/{id}             (poll status)

POST   /api/jobs
GET    /api/jobs
GET    /api/jobs/{id}
DELETE /api/jobs/{id}
POST   /api/jobs/{id}/analyse           (body: resumeId; 202, returns match_analysis id)
GET    /api/match-analyses/{id}
GET    /api/match-analyses/{id}/evidence
POST   /api/jobs/compare                (body: jobIds[]; returns comparison matrix)

POST   /api/applications
GET    /api/applications
GET    /api/applications/{id}
PATCH  /api/applications/{id}/status
GET    /api/applications/{id}/history

GET    /api/dashboard
```

Standard REST status codes throughout (201 on create, 202 for async kickoff, 404 vs 403 handled carefully — see §10), global `@ControllerAdvice` exception handler mapping domain exceptions to a consistent error body, Bean Validation on all request DTOs, OpenAPI/Swagger generated from annotations.

## 8. Matching & Scoring Algorithm

**Revised weights** (I changed your proposal — reasoning below):

| Component | Weight |
|---|---|
| Required skills | 35% |
| Preferred skills | 10% |
| Experience relevance (years + title/seniority) | 20% |
| Responsibilities / domain semantic match | 20% |
| Education & certifications | 10% |
| Soft skills | 5% |

Why the change: your original gave Responsibilities only 15% and no explicit line for soft skills, but responsibility-bullet similarity is actually one of the strongest real signals of fit (it captures "have you actually done this job" better than a skill list does), so it deserves real weight. Soft skills are low-weight but shouldn't be zero — many JDs state them explicitly and they're cheap to detect from resume language. Required skills dropped from 40% to 35% to make room; preferred skills dropped from 15% to 10% since by definition they're not deal-breakers and shouldn't swing the score much.

**Pipeline (deterministic, LLM used only where marked):**

1. Normalize every skill mention on both sides through the `skills`/`skill_aliases` dictionary lookup (exact match first — deterministic, free, instant).
2. For unmatched terms only, use embeddings (cosine similarity) or an LLM call to propose equivalence, tagged `INFERRED` with a stored confidence score. Never silently promoted to `EXPLICIT`.
3. Required skills: explicit match → full points; inferred match above a confidence threshold → partial credit (e.g. 60%); no match → 0, flagged `ABSENT`.
4. Preferred skills: same logic, lower weight.
5. Experience: years computed deterministically from `experiences` date ranges (no LLM needed); title/seniority relevance via embedding similarity between JD title and resume titles.
6. Responsibilities: sentence-level embedding similarity between each `job_requirements` (type=RESPONSIBILITY) row and every `experience_highlights` row; bucket into strong/partial/none by similarity thresholds. Same embeddings + same thresholds every run — this is "AI-assisted" but fully reproducible and explainable (you can show the actual similarity score).
7. Education: rule-based — does the resume have a degree at or above the required level in a related field.
8. Weighted sum → overall score, each component stored in `score_components` so the UI can render the exact breakdown, not just a final number.

**Recommendation logic** (rule-based, not LLM-decided):
- Strong Match: ≥80%, no missing hard requirement.
- Reasonable Match: 60–79%.
- Stretch Application: 40–59%, or ≥80% but missing one hard "must-have."
- Poor Match: <40%.

A missing hard requirement always caps the recommendation one tier down, regardless of overall score — this is the rule that stops a high score from producing a nonsensical "apply" verdict.

## 9. Where AI Should / Should Not Be Used

**Use AI for:** structuring ambiguous JD text into `job_requirements` rows; assisting resume section/bullet extraction (on top of deterministic PDFBox text extraction, not instead of it); proposing semantic skill equivalence (always confidence-tagged, always inferred not explicit); generating the natural-language "why" explanation from an *already-computed* score breakdown; generating resume-quality feedback text; generating embeddings for similarity scoring.

**Never use AI for:** computing the final score or component points, deciding the recommendation tier, inventing skills/experience not present in the source text, or being the only source of a match with no stored evidence.

Wrap all LLM/embedding calls behind an `AiClient` interface (adapter pattern) so the provider is swappable and every service layer test can use a deterministic fake implementation instead of hitting a real API.

## 10. Security / Privacy

- Bcrypt password hashing, JWT access token (short-lived) + refresh token rotation. (Trade-off worth knowing for interviews: httpOnly-cookie sessions with CSRF protection are arguably more secure against XSS token theft than JWT-in-localStorage; JWT is used here because it's the expected pattern for a Spring/React portfolio split, and refresh rotation mitigates the main risk.)
- Files never stored in Postgres — S3 with per-user key prefixing, presigned URLs for retrieval, server-side encryption (SSE-S3), magic-byte validation (not just file extension) and size limits on upload.
- **Ownership checks on every resource fetch** — every service method that loads a `Resume`, `Job`, `Application`, etc. must verify `resource.userId == currentUser.id`, returning 404 (not 403) to avoid leaking existence. This is the IDOR class of bug and it's worth writing explicit tests for it — a good interview talking point.
- Resume deletion is a hard delete: removes the S3 object and cascades the DB rows. No silent retention.
- Structured logging with PII redaction — never log raw resume text or emails.
- Rate limiting on `/api/auth/*` (bucket4j) — small addition, real value.
- Secrets via environment variables / AWS Secrets Manager, never committed; `.env.example` in repo.
- Before sending resume text to a third-party LLM, strip direct identifiers (name, email, phone) from the prompt where feasible — reduces PII exposure to the AI provider.

## 11. Testing Strategy

- **Unit tests**: scoring algorithm as pure, table-driven tests (this is your highest-value test surface — many input combinations, no I/O); skill normalization; recommendation-tier logic. Mockito for repository/AI-client mocks in service tests.
- **Integration tests**: Testcontainers-backed Postgres for repository tests and full API flows (MockMvc) — auth → upload → parse → analyse → evidence, using a deterministic fake `AiClient` so tests are stable and free.
- **Contract tests** on the `AiClient` interface (given fixed input, expect a specific shape) rather than asserting on real LLM output content.
- Frontend: component tests (Vitest/RTL) for core screens; one Playwright/Cypress smoke test covering upload → analyze → view score is a strong, low-cost differentiator if time allows — optional, not MVP-blocking.
- CI runs unit + integration suites against a Postgres service container on every PR.

## 12. Deployment Strategy

- Docker Compose for local dev: API + Postgres + frontend.
- GitHub Actions: build → test → lint → build images → push to GHCR (cheaper/simpler than ECR for a portfolio project, still shows real CI/CD) → deploy.
- AWS footprint kept deliberately small: RDS Postgres (db.t4g.micro, free-tier eligible — looks more "real" on a CV than Postgres-in-a-container, worth the small cost), S3 for resumes, ECS Fargate for the backend container (no EC2 management, no Kubernetes), S3+CloudFront for the static frontend build.
- Flyway migrations run automatically on deploy.
- Config via Spring profiles (`dev`/`test`/`prod`) + AWS Secrets Manager/SSM for secrets.

## 13. Implementation Roadmap

1. **Setup** — repo scaffold, Docker Compose, Flyway baseline schema, CI skeleton, architecture + ER diagrams.
2. **Auth + user module** — register/login/JWT, React shell + routing/login screen.
3. **Resume upload & parsing** — PDFBox text extraction, structuring pipeline, user-edit/confirm UI (build trust in the data before anything downstream uses it).
4. **Job input & JD parsing** — paste flow, `job_requirements` structuring, skill taxonomy/alias tables.
5. **Matching & scoring engine** — deterministic core, heavily unit-tested, evidence linking.
6. **Job Analysis screen** — the showcase UI, Should-I-Apply logic.
7. **Applications tracking** — status lifecycle + history.
8. **Dashboard** — aggregation queries.
9. **Job comparison** — matrix view (can be pulled earlier if convenient — low coupling to other phases).
10. **Resume Quality Analysis** — standalone, low-dependency, can also be pulled earlier.
11. **Hardening** — security pass (IDOR tests, rate limiting), logging/observability, UI polish, README, deploy to AWS.

We'll go through these one at a time, each with its own tests before moving to the next — not all at once.

## 14. Technical Risks

- **PDF parsing is genuinely messy** (multi-column layouts, tables, creative formatting). Mitigation: PDFBox for extraction, LLM-assisted structuring on top, and — most importantly — a mandatory human review/edit step before the parsed data is used for anything. Don't oversell "fully automatic perfect extraction"; the correction UI is itself a feature, not a fallback to be ashamed of.
- **LLM cost/latency/rate limits** — mitigate with async processing + polling, response caching, and an adapter that can swap to a cheaper/faster model.
- **Semantic-match false positives/negatives** — confidence thresholds, always show the evidence so the user can judge for themselves, never hide the reasoning.
- **Scope creep** — this is the single biggest risk given the length of the original feature list. Hold the line on the MVP scope in §2 before touching anything in §3.
- **Demo data privacy** — use synthetic resumes/JDs for screenshots and demos, not your real one; note this in the README.
- **Premature module-splitting into services** — don't. Keep it one deployable; enforce boundaries in code only.

## 15. CV Standout Value

The evidence-linked, deterministic scoring engine is the headline: it shows data modeling and reasoning-under-uncertainty, not just CRUD-plus-a-prompt. The `AiClient` adapter pattern is a real architecture decision, not framework glue. Testcontainers-backed integration tests, a real CI pipeline, and explicit IDOR-prevention tests show security awareness beyond "add Spring Security and stop." The mandatory human-correction step on parsed data shows product judgment. A genuinely normalized 16-table schema with real relationships is rare in portfolio projects at this scope.

## 16. Distinctive but Sane Ideas

1. **Explicit / Inferred / Absent surfaced directly in the UI**, click-through to the exact resume sentence behind each match. This single UX detail is what separates JobFit from every "AI resume scorer" clone.
2. **Resume Quality Analysis as a standalone feature**, independent of any job — a rule-based writing linter (weak verbs, missing quantification, vague adjectives, ATS-parseability issues) with optional LLM-polish suggestions. Most similar projects only do job matching; this is a second, genuinely separate value prop.
3. **Configurable scoring weights** (or at minimum, clearly documented and justified defaults) — shows you understand a single fixed formula doesn't fit every role/seniority.
4. **Job comparison matrix** — practically useful for deciding where to focus effort, and technically interesting (pivoting matched requirements across multiple jobs).
5. **"Relevant but poorly communicated" detector** — flag resume bullets with medium (not high, not zero) similarity to a JD requirement as "possibly relevant — consider rewriting more explicitly," with an AI-suggested rewrite that is *never* auto-applied. This directly operationalizes your "don't encourage lying" principle and is a genuinely thoughtful, rare feature.

---

*Next step: agree scope, then implement Phase 1 (auth + user module) incrementally rather than all at once.*
