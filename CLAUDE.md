# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot REST API to track job applications backed by PostgreSQL. Includes full CRUD, soft delete, and an async AI scoring pipeline that analyzes job descriptions against a resume using OpenRouter LLMs.

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.4.x
- **Database**: PostgreSQL 16 (via Docker/OrbStack)
- **Migrations**: Flyway (runs automatically on startup)
- **ORM**: Spring Data JPA
- **Mapping**: MapStruct 1.6.3 (with `ReportingPolicy.ERROR`)
- **AI**: OpenRouter API (free-tier models) via Spring `RestClient`
- **Build**: Gradle
- **CI/CD**: GitHub Actions + Testcontainers
- **Testing**: JUnit 5, Testcontainers, WireMock, MockRestServiceServer, Awaitility

## Common Commands

```bash
# Start PostgreSQL
docker-compose up -d

# Run the application (requires .env vars exported)
set -a && source .env && set +a && ./gradlew bootRun

# Run tests (Testcontainers spins a real PostgreSQL automatically)
./gradlew test

# Run a single test class
./gradlew test --tests "com.jobtracker.SomeTest"

# Build without running tests
./gradlew build -x test

# Verify data in local DB
docker exec job-tracker-db psql -U jobuser -d jobtracker -c "SELECT * FROM jobs;"
```

## Architecture

Standard layered Spring Boot architecture under `com.jobtracker`:

- **`controller/`** — thin REST controllers, no business logic
- **`service/`** — all business logic lives here (`@Service` classes)
- **`repository/`** — Spring Data JPA repositories
- **`model/`** — JPA entities (Job, AgentRun, Score)
- **`dto/`** — Java records for request/response DTOs
- **`mapper/`** — MapStruct mappers (entity <-> DTO)
- **`enums/`** — `JobStatus` (with `isPreApplication()` flag), `AgentRunStatus`
- **`agents/`** — AI agent orchestration: `JdParserAgent`, `ResumeScorerAgent`, `OrchestratorService`
- **`client/`** — `OpenRouterClient` wrapping RestClient for LLM calls
- **`config/`** — `OpenRouterProperties` (`@ConfigurationProperties`), `AsyncConfig`, `RestClientConfig`
- **`exception/`** — `GlobalExceptionHandler` (`@RestControllerAdvice`), `JobNotFoundException`

Flyway migrations live in `src/main/resources/db/migration/` (V1-V5). Never edit a migration after it has run — always write a new one.

### AI Scoring Pipeline

`POST /api/v1/jobs/{id}/analyze` triggers an async pipeline (`@Async("agentExecutor")`):
1. **JdParserAgent** — extracts skills, seniority, domain from JD text
2. **ResumeScorerAgent** — scores resume fit (0-100) with 3 recommendations

Each step is logged in `agent_runs` table. Results saved to `scores` table and `jobs.fit_score`.

`AgentJsonExtractor` handles LLM output parsing — strips markdown fences, extracts first JSON block.

### Soft Delete

Jobs use `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")` for transparent soft delete. The `deleted_at` column has no setter — managed only by the SQL override.

### appliedAt Defaulting

When status changes to a non-pre-application status (anything except UNDETERMINED/NOT_A_FIT) and `appliedAt` is null, it auto-defaults to current server time. Controlled by `JobStatus.isPreApplication()`.

## Database Conventions

- All tables use `snake_case`
- Every table must have: `id` (UUID, auto-generated PK), `created_at`
- `jobs` also has `updated_at`
- Base package: `com.jobtracker`

## API

| Method | Path                        | Description                        |
|--------|-----------------------------|------------------------------------|
| POST   | /api/v1/jobs                | Create a job application           |
| GET    | /api/v1/jobs                | List all non-deleted jobs           |
| GET    | /api/v1/jobs/{id}           | Get a single job                   |
| PATCH  | /api/v1/jobs/{id}           | Partial update (status, notes)     |
| DELETE | /api/v1/jobs/{id}           | Soft delete (204)                  |
| POST   | /api/v1/jobs/{id}/analyze   | Trigger async AI analysis (202)    |
| GET    | /api/v1/jobs/{id}/score     | Get latest AI score                |

## Configuration

OpenRouter config via `@ConfigurationProperties(prefix = "openrouter")` — requires `OPENROUTER_API_KEY` env var. Model defaults to a free-tier model (configured in `application.yml`).

`RestClientConfig` forces HTTP/1.1 via `RestClientCustomizer` — needed for WireMock compatibility in tests.

## CI/CD

GitHub Actions (`.github/workflows/pr-check.yml`) runs on every PR: compile + tests using Testcontainers (no manual DB setup needed in CI).
