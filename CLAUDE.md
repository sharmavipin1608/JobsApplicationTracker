# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot REST API to track job applications backed by PostgreSQL. V1 scope: `POST /api/v1/jobs` to insert a job record.

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL 16 (via Docker)
- **Migrations**: Flyway (runs automatically on startup)
- **ORM**: Spring Data JPA
- **Build**: Gradle
- **CI/CD**: GitHub Actions + Testcontainers

## Common Commands

```bash
# Start PostgreSQL
docker-compose up -d

# Run the application
./gradlew bootRun

# Run tests (Testcontainers spins a real PostgreSQL automatically)
./gradlew test

# Run a single test class
./gradlew test --tests "com.jobtracker.SomeTest"

# Build without running tests
./gradlew build -x test

# Verify data in local DB
docker exec -it job-tracker-db psql -U jobuser -d jobtracker -c "SELECT * FROM jobs;"
```

## Architecture

Standard layered Spring Boot architecture under `com.jobtracker`:

- **`controller/`** — thin REST controllers, no business logic
- **`service/`** — all business logic lives here (`@Service` classes)
- **`repository/`** — Spring Data JPA repositories
- **`model/`** — JPA entities

Flyway migrations live in `src/main/resources/db/migration/` and run on every startup. Never edit a migration after it has run — always write a new one.

## Database Conventions

- All tables use `snake_case`
- Every table must have: `id` (UUID, auto-generated PK), `created_at`, `updated_at`
- Base package: `com.jobtracker`

## API

| Method | Path         | Description              |
|--------|--------------|--------------------------|
| POST   | /api/v1/jobs | Insert a job application |

Request body fields: `company`, `role`, `jdText` (optional), `appliedAt`.
Response includes: `id`, `company`, `role`, `status` (default: `APPLIED`), `appliedAt`, `createdAt`.

## CI/CD

GitHub Actions (`.github/workflows/pr-check.yml`) runs on every PR: compile + tests using Testcontainers (no manual DB setup needed in CI).
