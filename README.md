# JobFit — Resume & Job Match Intelligence Platform

An explainable job-matching and application-tracking tool. Every fit score, match,
and recommendation traces back to a specific resume sentence and a specific job
requirement, through a deterministic scoring engine — AI is used for interpretation
and language generation, never as the sole arbiter of fit.

Full design rationale: see `docs/JobFit_Design_v1.md` (product scope, architecture,
schema, scoring algorithm, security model, roadmap).


## Stack

Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, PostgreSQL, Flyway.
Frontend: React 18, TypeScript, Vite.
Infra: Docker, GitHub Actions, AWS (ECS Fargate + RDS + S3).
Testing: JUnit 5, Mockito, AssertJ, Testcontainers.

## Architecture

Modular monolith (see `docs/architecture.mmd`) — one deployable Spring Boot
application with enforced module boundaries in code (`user`, `resume`,
`resumeparsing`, `job`, `jobparsing`, `matching`, `scoring`, `application`,
`analytics`, `resumequality`, `ai`, `common`). Each module owns its own JPA
repositories; cross-module calls go through service interfaces only. `analytics`
and `resumequality` are both read-only/low-dependency: they aggregate or analyse
data other modules already computed and persisted (jobs, applications, match
analyses, evidence, resume skills) — neither one scores, writes, or calls the AI layer.

## Running locally

Requires: JDK 21, Maven, Node 22, Docker (for Postgres, or run it however you like).

```bash
cp .env.example .env
docker compose up -d postgres

cd backend
mvn spring-boot:run

# in a second terminal
cd frontend
npm install
npm run dev
```

API docs: http://localhost:8080/swagger-ui.html
Frontend: http://localhost:5173

## Running tests

```bash
cd backend
mvn test           # fast unit tests (Mockito, no external deps)
mvn verify          # + Testcontainers integration tests (requires Docker)
```

```bash
cd frontend
npm run test
```

## Database migrations

Flyway migrations live in `backend/src/main/resources/db/migration`, one file per
schema change, applied automatically on backend startup. `V1__create_users_table.sql`
is the Phase 1 baseline; later phases add their own tables incrementally rather than
one large upfront schema, so the migration history reflects how the product was
actually built.

## Security notes

- Passwords hashed with bcrypt; never logged or returned in any response.
- JWT access tokens are short-lived (15 min default); refresh tokens are opaque
  random values, stored server-side only as a SHA-256 hash, and rotated on every use
  (old token revoked the moment a new one is issued).
- **IDOR prevention**: every service method that loads a user-owned resource scopes
  the query by the authenticated user's id (`findByIdAndUserId`, see
  `common/util/SecurityUtils`) and returns 404 (never 403) on mismatch, so a caller
  can't distinguish "not yours" from "doesn't exist." Explicitly tested per module -
  `ResumeServiceTest`, `JobServiceTest`, `ApplicationServiceTest`,
  `MatchAnalysisServiceTest`, `JobComparisonServiceTest`, `ResumeQualityServiceTest` -
  rather than left as an incidental side effect of the repository pattern.
- **Rate limiting**: `common/security/RateLimitingFilter` caps `/api/auth/**`
  (register/login/refresh - the brute-force/credential-stuffing surface) at a
  configurable requests-per-minute per IP+path, hand-rolled and in-memory rather than
  pulling in Redis for a single-instance deployment (documented limitation - see the
  class Javadoc and `docs/DEPLOYMENT.md`).
- **Response hardening**: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  and HSTS on every response (`SecurityConfig`); the default Spring Boot error
  fallback never echoes exception messages or stack traces
  (`server.error.include-message: never` in `application.yml`) - every error a
  client actually sees is a human-written message from `GlobalExceptionHandler`.
- Resume files (from Phase 2 onward) are stored in S3, never in the database; hard
  delete removes the object and cascades DB rows, no silent retention.
- `/actuator/health` exposes status only (`show-details: never`) - no dependency or
  environment detail leaked to an unauthenticated caller.

## Deployment

See `docs/DEPLOYMENT.md` for the full runbook. Summary: both services are
Docker images (`backend/Dockerfile`, `frontend/Dockerfile`) intended for ECS
Fargate, behind an ALB, with RDS Postgres and S3 for resume storage. Task
definition templates live in `infra/`. `.github/workflows/deploy.yml` is a
manual (`workflow_dispatch`), OIDC-authenticated GitHub Actions workflow that
builds, pushes to ECR, and rolls out new ECS task revisions - it only does
anything once the AWS resources it assumes already exist; nothing deploys
automatically on push, and no live instance of this app is hosted anywhere.

## Project layout

```
jobfit/
  backend/    Spring Boot API (package-by-module under com.jobfit)
  frontend/   React + TypeScript SPA
  infra/      ECS task definition templates
  docs/       design doc, architecture diagram, ER diagram, deployment runbook
  .github/    CI workflow (every push/PR) + deploy workflow (manual, AWS)
```
