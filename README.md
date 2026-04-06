# Job Application Tracker — V1

A Spring Boot REST API to track job applications backed by PostgreSQL.

V1 scope: insert a job record via REST API and verify it in the database.
Nothing more.

---

## Tech Stack

| Layer      | Technology                        |
|------------|-----------------------------------|
| Language   | Java 21                           |
| Framework  | Spring Boot 3.x                   |
| Database   | PostgreSQL 16 (Docker)            |
| Migrations | Flyway                            |
| ORM        | Spring Data JPA                   |
| Build      | Gradle                            |
| CI/CD      | GitHub Actions + Testcontainers   |

---

## V1 Deliverables

- [ ] `docker-compose.yml` — PostgreSQL running locally in Docker
- [ ] `V1__create_jobs.sql` — Flyway migration creating the jobs table
- [ ] Spring Boot project with one endpoint: `POST /api/v1/jobs`
- [ ] Verify inserted record appears in the database
- [ ] GitHub Actions workflow — run tests on every PR

---

## Database Schema

### jobs

| Column     | Type      | Notes                              |
|------------|-----------|------------------------------------|
| id         | UUID (PK) | auto-generated                     |
| company    | VARCHAR   | company name                       |
| role       | VARCHAR   | job title                          |
| jd_text    | TEXT      | job description (optional)         |
| status     | VARCHAR   | default: APPLIED                   |
| applied_at | TIMESTAMP | when you applied                   |
| created_at | TIMESTAMP | auto                               |
| updated_at | TIMESTAMP | auto                               |

---

## API

| Method | Path            | Description              |
|--------|-----------------|--------------------------|
| POST   | /api/v1/jobs    | Insert a job application |

### Request body
```json
{
  "company": "Acme Corp",
  "role": "Senior Software Engineer",
  "jdText": "optional job description text",
  "appliedAt": "2026-04-06T10:00:00"
}
```

### Response
```json
{
  "id": "uuid",
  "company": "Acme Corp",
  "role": "Senior Software Engineer",
  "status": "APPLIED",
  "appliedAt": "2026-04-06T10:00:00",
  "createdAt": "2026-04-06T10:00:01"
}
```

---

## Project Structure

```
job-tracker/
├── README.md
├── CLAUDE.md
├── docker-compose.yml
├── build.gradle
├── agents/
│   ├── schema.md       ← instructions for Schema Agent
│   ├── backend.md      ← instructions for Backend Agent
│   └── cicd.md         ← instructions for CI/CD Agent
├── .github/
│   └── workflows/
│       └── pr-check.yml
└── src/
    └── main/
        ├── java/com/jobtracker/
        │   ├── controller/
        │   │   └── JobController.java
        │   ├── service/
        │   │   └── JobService.java
        │   ├── repository/
        │   │   └── JobRepository.java
        │   └── model/
        │       └── Job.java
        └── resources/
            ├── application.yml
            └── db/migration/
                └── V1__create_jobs.sql
```

---

## Coding Conventions

- Controllers are thin — no business logic
- Business logic lives in `@Service` classes
- All tables use `snake_case`
- Every table has `id` (UUID), `created_at`, `updated_at`
- No credentials in code — use environment variables
- Never edit a Flyway migration after it has run — write a new one
- Base package: `com.jobtracker`

---

## Local Setup

### Prerequisites
- Java 21
- Gradle 8+
- Docker Desktop

### Run locally
```bash
# Start PostgreSQL
docker-compose up -d

# Run the app (Flyway migrations run automatically on startup)
./gradlew bootRun

# Test the endpoint
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{"company":"Acme Corp","role":"Senior Engineer"}'

# Verify in database
docker exec -it job-tracker-db psql -U jobuser -d jobtracker -c "SELECT * FROM jobs;"
```

---

## CI/CD

### On every Pull Request
- Compile
- Run tests (Testcontainers spins a real PostgreSQL — no manual setup needed)

---

## What Comes After V1

- `GET /api/v1/jobs` — list all applications
- `PUT /api/v1/jobs/{id}` — update status
- AI agent pipeline for resume scoring
- Multi-agent orchestration

But not today.
