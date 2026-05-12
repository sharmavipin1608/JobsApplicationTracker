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
| `PATCH`  | `/api/v1/jobs/{id}`         | Partial update (status, notes, jdUrl)    |
| `DELETE` | `/api/v1/jobs/{id}`         | Soft delete (204)                        |
| `POST`   | `/api/v1/jobs/{id}/analyze` | Trigger async AI analysis (202)          |
| `GET`    | `/api/v1/jobs/{id}/score`   | Get latest AI fit score                  |

### Resumes

| Method  | Path                              | Description                               |
|---------|-----------------------------------|-------------------------------------------|
| `POST`  | `/api/v1/resume`                  | Upload / replace master resume (PDF or .txt) |
| `GET`   | `/api/v1/resume`                  | Get current master resume metadata        |
| `GET`   | `/api/v1/resume/download`         | Download current master resume file       |
| `POST`  | `/api/v1/jobs/{id}/resume`        | Upload / replace tailored resume for a job |
| `GET`   | `/api/v1/jobs/{id}/resume`        | Get latest tailored resume for a job      |
| `GET`   | `/api/v1/jobs/{id}/resume/download` | Download tailored resume for a job      |

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

---

## Claude Desktop integration (MCP)

The API includes a Spring AI MCP (Model Context Protocol) server that lets Claude Desktop read and manage your job applications using natural language.

### How it works

When run with the `mcp` profile, the application starts as a STDIO MCP server (no HTTP port — Claude communicates via stdin/stdout). All logging is redirected to `/tmp/job-tracker-mcp.log` to keep stdout clean for the protocol.

### Available tools

| Tool | Description |
|---|---|
| `createJob` | Create a new job application (optionally triggers AI scoring if jdText is provided) |
| `listJobs` | List all active (non-deleted) applications |
| `getJob` | Get a single application by UUID |
| `updateJob` | Update status, notes, or JD URL |
| `deleteJob` | Soft-delete an application |
| `analyzeAndWait` | Trigger AI fit scoring and block until a result arrives (up to 60 s) |
| `getMasterResume` | Get the current master resume metadata |

### Setup

1. Build the jar:
   ```bash
   ./gradlew build -x test
   ```

2. Add this entry to your Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json`):
   ```json
   {
     "mcpServers": {
       "job-tracker": {
         "command": "java",
         "args": [
           "-jar", "/absolute/path/to/build/libs/JobsApplicationTracker-0.0.1-SNAPSHOT.jar",
           "--spring.profiles.active=mcp"
         ],
         "env": {
           "DB_NAME": "jobtracker",
           "DB_USER": "jobuser",
           "DB_PASSWORD": "changeme",
           "DB_PORT": "5432",
           "OPENROUTER_API_KEY": "sk-or-v1-your-key"
         }
       }
     }
   }
   ```

3. Restart Claude Desktop. You can now say things like "Show me all my job applications" or "Mark the Vercel application as INTERVIEWING".

> **Note:** The MCP server and the HTTP API share the same database. You can run both simultaneously — they don't conflict.

---

## Security

### CORS
`WebConfig.java` restricts cross-origin requests to `http://localhost:3000` (the Next.js dev server) for all `/api/**` routes. Update `allowedOrigins` before deploying to production.

### No authentication
All REST endpoints are publicly accessible — there is no authentication layer. This is intentional for local development. Add Spring Security (or an API gateway) before exposing this to the internet.

### Secrets management
All sensitive values (`OPENROUTER_API_KEY`, database credentials) are loaded from environment variables. The `.env` file is git-ignored — never commit it.

The `OPENROUTER_API_KEY` is required only when AI analysis (`POST /api/v1/jobs/{id}/analyze`) is called. All other endpoints work without it.

### Resume storage
Resumes are stored as raw bytes (`BYTEA`) in PostgreSQL alongside extracted plain text. Files are only served via `Content-Disposition: attachment` — they cannot be rendered inline. There is no file size enforcement at the application layer; rely on your reverse proxy or load balancer for upload size limits in production (Apache PDFBox handles parsing errors gracefully and returns `UNSUPPORTED_FILE_TYPE` for non-PDF/text content).

### Soft delete
Jobs are never permanently deleted via the `DELETE` endpoint. The `deleted_at` column is set and the row is hidden from all queries via `@SQLRestriction`. A job can only be permanently removed by directly querying the database.

---

## Integration with the UI

The companion Next.js frontend ([Job Tracker UI](https://github.com/sharmavipin1608/JobTracker-UI-v0)) connects to this API.

### Quick start (both running locally)

```bash
# 1. Start this API (Docker, recommended):
docker-compose up --build

# 2. In the UI repo:
echo "NEXT_PUBLIC_API_BASE_URL=http://localhost:8080" > .env.local
npm install && npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

### How the UI uses the API

| UI feature | Endpoint(s) used |
|---|---|
| Job list | `GET /api/v1/jobs` |
| Create/edit job | `POST /api/v1/jobs`, `PATCH /api/v1/jobs/{id}` |
| Delete job | `DELETE /api/v1/jobs/{id}` |
| Analyze fit | `POST /api/v1/jobs/{id}/analyze` + polling `GET /api/v1/jobs/{id}/score` |
| Master resume chip | `GET /api/v1/resume`, `POST /api/v1/resume`, `GET /api/v1/resume/download` |
| Tailored resume section | `GET /api/v1/jobs/{id}/resume`, `POST /api/v1/jobs/{id}/resume`, `GET /api/v1/jobs/{id}/resume/download` |
