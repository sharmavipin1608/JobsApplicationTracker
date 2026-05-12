# Job Application Tracker

A Spring Boot REST API to track job applications backed by PostgreSQL. Includes full CRUD, soft delete, resume management, and an async AI scoring pipeline that analyzes job descriptions against your resume using OpenRouter LLMs.

---

## Tech Stack

| Layer          | Technology                          |
|----------------|-------------------------------------|
| Language       | Java 21                             |
| Framework      | Spring Boot 3.4.x                   |
| Database       | PostgreSQL 16 (Docker)              |
| Migrations     | Flyway (V1–V7)                      |
| ORM            | Spring Data JPA                     |
| Mapping        | MapStruct 1.6.3                     |
| PDF parsing    | Apache PDFBox 3.0.4                 |
| AI             | OpenRouter API (free-tier LLMs)     |
| Build          | Gradle                              |
| CI/CD          | GitHub Actions + Testcontainers     |
| Testing        | JUnit 5, Testcontainers, WireMock   |

---

## API

### Jobs

| Method   | Path                        | Description                              |
|----------|-----------------------------|------------------------------------------|
| `POST`   | `/api/v1/jobs`              | Create a job application                 |
| `GET`    | `/api/v1/jobs`              | List all non-deleted jobs                |
| `GET`    | `/api/v1/jobs/{id}`         | Get a single job                         |
| `PATCH`  | `/api/v1/jobs/{id}`         | Partial update (status, notes)           |
| `DELETE` | `/api/v1/jobs/{id}`         | Soft delete (204)                        |
| `POST`   | `/api/v1/jobs/{id}/analyze` | Trigger async AI analysis (202)          |
| `GET`    | `/api/v1/jobs/{id}/score`   | Get latest AI fit score                  |

### Resumes

| Method   | Path                              | Description                              |
|----------|-----------------------------------|------------------------------------------|
| `POST`   | `/api/v1/resumes`                 | Upload master resume (PDF/text)          |
| `POST`   | `/api/v1/resumes?jobId={id}`      | Upload job-tailored resume               |
| `GET`    | `/api/v1/resumes/master`          | Get current master resume metadata       |
| `GET`    | `/api/v1/resumes/master/download` | Download current master resume file      |
| `GET`    | `/api/v1/resumes/job/{jobId}`     | Get latest tailored resume for a job     |

---

## Running with Docker (recommended)

This is the easiest way to run the full stack. You only need **Docker** installed — no Java, no Gradle, nothing else.

Both the app and the database run as containers. The app container builds from source; the database runs the official `postgres:16` image.

### 1. Create a `.env` file

Create a `.env` file in the project root with the following content:

```env
DB_NAME=jobtracker
DB_USER=jobuser
DB_PASSWORD=changeme
DB_PORT=5432
OPENROUTER_API_KEY=sk-or-v1-your-key-here
```

Get your free API key at [openrouter.ai](https://openrouter.ai).

> `.env` is git-ignored — never commit it.

### 2. Start everything

```bash
docker-compose up --build
```

This will:
- Pull the `postgres:16` image and start the database
- Build the app image from source
- Run Flyway migrations automatically on startup
- Start the API on `http://localhost:8080`

### 3. Verify it's running

```bash
curl http://localhost:8080/api/v1/jobs
# Expected: []
```

### 4. Stop

```bash
docker-compose down
```

To also delete the database volume (wipe all data):

```bash
docker-compose down -v
```

---

### Sharing a pre-built image

If you want to run this on another machine *without* the source code, push the image to a registry first:

```bash
# Build and tag
docker build -t your-dockerhub-username/job-tracker:latest .

# Push
docker push your-dockerhub-username/job-tracker:latest
```

Then in `docker-compose.yml`, replace:
```yaml
app:
  build: .
```
with:
```yaml
app:
  image: your-dockerhub-username/job-tracker:latest
```

The recipient only needs Docker, the modified `docker-compose.yml`, and a `.env` file.

---

## Running locally (without Docker for the app)

If you prefer to run the app directly with Gradle (e.g. for faster development iteration):

### Prerequisites
- Java 21
- Docker (for PostgreSQL only)

```bash
# Start PostgreSQL only
docker-compose up -d db

# Export env vars and run
set -a && source .env && set +a && ./gradlew bootRun
```

---

## Running tests

Tests use Testcontainers — they spin up a real PostgreSQL automatically. No manual database setup needed.

```bash
# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.jobtracker.SomeTest"
```

---

## Database Schema

### jobs

| Column       | Type         | Notes                                          |
|--------------|--------------|------------------------------------------------|
| `id`         | UUID (PK)    | auto-generated                                 |
| `company`    | VARCHAR(255) | required                                       |
| `role`       | VARCHAR(255) | required                                       |
| `jd_text`    | TEXT         | optional — used for AI analysis               |
| `jd_url`     | VARCHAR(2048)| optional — link to the original job posting    |
| `status`     | VARCHAR(50)  | enum, default: UNDETERMINED                    |
| `applied_at` | TIMESTAMP    | nullable — auto-defaults when status advances  |
| `notes`      | TEXT         | optional                                       |
| `fit_score`  | INTEGER      | 0–100, set by AI pipeline                      |
| `created_at` | TIMESTAMP    | auto                                           |
| `updated_at` | TIMESTAMP    | auto                                           |
| `deleted_at` | TIMESTAMP    | soft delete marker                             |

### resumes

| Column         | Type         | Notes                                          |
|----------------|--------------|------------------------------------------------|
| `id`           | UUID (PK)    | auto-generated                                 |
| `job_id`       | UUID (FK)    | nullable — NULL means master resume            |
| `file_name`    | VARCHAR(255) | original filename                              |
| `file_content` | BYTEA        | raw file bytes (lazy-loaded)                   |
| `content_text` | TEXT         | extracted plain text, used by AI scorer        |
| `created_at`   | TIMESTAMP    | auto                                           |

---

## Job Status Lifecycle

| Status           | Meaning                                              |
|------------------|------------------------------------------------------|
| `UNDETERMINED`   | Just added, not reviewed yet — **default**           |
| `NOT_A_FIT`      | Decided not to apply                                 |
| `APPLIED`        | Submitted the application                            |
| `SCREENING`      | Recruiter screen scheduled or done                   |
| `INTERVIEWING`   | In the interview process                             |
| `OFFER_RECEIVED` | Got an offer                                         |
| `OFFER_ACCEPTED` | Accepted the offer                                   |
| `OFFER_DECLINED` | Declined the offer                                   |
| `REJECTED`       | Rejected at any stage                                |
| `WITHDRAWN`      | You pulled out of the process                        |
| `GHOSTED`        | No response after a reasonable time                  |

`UNDETERMINED` and `NOT_A_FIT` are pre-application statuses — `applied_at` stays null. All other statuses auto-set `applied_at` to current server time if it isn't already set.

---

## Verify data in the local database

```bash
docker exec job-tracker-db psql -U jobuser -d jobtracker -c "SELECT id, company, role, status, fit_score FROM jobs;"
```

---

## CI/CD

GitHub Actions (`.github/workflows/pr-check.yml`) runs on every PR: compile + full test suite. Testcontainers handles the database — no manual setup needed in CI.
