# Job Application Tracker — V1

A Spring Boot REST API to track job applications backed by PostgreSQL. Designed to help evaluate jobs before applying — not just track applications already submitted.

V1 scope: insert a job record via REST API and verify it in the database.

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

| Column     | Type         | Notes                                                     |
|------------|--------------|-----------------------------------------------------------|
| id         | UUID (PK)    | auto-generated                                            |
| company    | VARCHAR(255) | company name                                              |
| role       | VARCHAR(255) | job title                                                 |
| jd_text    | TEXT         | job description (optional)                                |
| status     | VARCHAR(50)  | enum, default: UNDETERMINED, DB CHECK constraint enforced |
| applied_at | TIMESTAMP    | nullable — only set when status is APPLIED or beyond      |
| created_at | TIMESTAMP    | auto                                                      |
| updated_at | TIMESTAMP    | auto                                                      |

---

## Job Status Lifecycle

Status is a Java enum (`JobStatus`) stored as a VARCHAR string in the DB.

| Status | Meaning |
|---|---|
| `UNDETERMINED` | Just added, not reviewed yet — **default** |
| `NOT_A_FIT` | Match score too low, decided not to apply |
| `APPLIED` | Submitted the application |
| `SCREENING` | Recruiter screen scheduled or done |
| `INTERVIEWING` | In the interview process |
| `OFFER_RECEIVED` | Got an offer |
| `OFFER_ACCEPTED` | Accepted the offer |
| `OFFER_DECLINED` | Declined the offer |
| `REJECTED` | Rejected at any stage |
| `WITHDRAWN` | You pulled out of the process |
| `GHOSTED` | No response after a reasonable time |

---

## API

| Method | Path         | Description         |
|--------|--------------|---------------------|
| POST   | /api/v1/jobs | Insert a job record |

### Request body
```json
{
  "company": "Acme Corp",
  "role": "Senior Software Engineer",
  "jdText": "optional job description text",
  "status": "UNDETERMINED",
  "appliedAt": "2026-04-06T10:00:00"
}
```

- `company` and `role` are required
- `status` is optional — defaults to `UNDETERMINED` if omitted
- `jdText` and `appliedAt` are optional

### Response — 201 Created
```json
{
  "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "company": "Acme Corp",
  "role": "Senior Software Engineer",
  "status": "UNDETERMINED",
  "appliedAt": null,
  "createdAt": "2026-04-06T10:00:01"
}
```

---

## Project Structure

```
job-tracker/
├── ReadMe.md
├── CLAUDE.md
├── docker-compose.yml
├── build.gradle
├── specs/
│   └── v1.md                        ← source of truth for V1 (what to build)
├── agents/
│   ├── orchestrator.md              ← coordinates all agents
│   ├── schema.md                    ← Flyway migrations
│   ├── backend.md                   ← Spring Boot layers
│   ├── testing.md                   ← tests per layer
│   ├── cicd.md                      ← GitHub Actions
│   └── postman.md                   ← Postman collection
├── postman/
│   └── job-tracker.postman_collection.json
├── .github/
│   └── workflows/
│       └── pr-check.yml
└── src/
    ├── main/
    │   ├── java/com/jobtracker/
    │   │   ├── controller/
    │   │   │   └── JobController.java
    │   │   ├── service/
    │   │   │   └── JobService.java
    │   │   ├── repository/
    │   │   │   └── JobRepository.java
    │   │   ├── model/
    │   │   │   └── Job.java
    │   │   ├── enums/
    │   │   │   └── JobStatus.java
    │   │   └── dto/
    │   │       ├── CreateJobRequest.java
    │   │       └── JobResponse.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V1__create_jobs.sql
    └── test/java/com/jobtracker/
        ├── controller/
        │   └── JobControllerTest.java
        ├── service/
        │   └── JobServiceTest.java
        └── repository/
            └── JobRepositoryTest.java
```

---

## Coding Conventions

- Controllers are thin — no business logic
- Business logic lives in `@Service` classes
- All tables use `snake_case`
- Every table has `id` (UUID), `created_at`, `updated_at`
- Never edit a Flyway migration after it has run — write a new one
- Base package: `com.jobtracker`
- Status stored as `@Enumerated(EnumType.STRING)` — never use ordinal mapping

---

## Local Setup

### Prerequisites
- Java 21
- Docker (Rancher Desktop or Docker Desktop)

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
- AI agent pipeline for resume/JD match scoring
- Status auto-suggestion based on match score
- Multi-agent orchestration

But not today.
