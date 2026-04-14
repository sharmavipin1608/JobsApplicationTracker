# Job Tracker MCP Server

Exposes the Job Application Tracker REST API as Claude tools, so Claude Desktop can manage your job applications conversationally.

## How it works

Claude Desktop launches this Python process and communicates with it over stdio (stdin/stdout). When you say something like *"add a job at Stripe"*, Claude calls the `create_job` tool, which makes a real HTTP request to your running Spring Boot API and returns the result.

```
Claude Desktop → server.py → http://localhost:8080 → PostgreSQL
```

No extra ports or services needed. The MCP server is just a process.

## Prerequisites

- Python 3.10+ (the `mcp` library requires it — system macOS Python is 3.9, install via Homebrew if needed)
- The Spring Boot API running (see root `README.md`)

```bash
# Install Python 3.13 if you don't have 3.10+ already
brew install python@3.13
```

## Setup

### 1. Create a virtual environment and install dependencies

A virtual environment is an isolated Python installation so these packages don't conflict with anything else on your machine.

```bash
cd mcp-server
/opt/homebrew/bin/python3.13 -m venv .venv
source .venv/bin/activate
pip install "mcp[cli]" httpx
```

### 2. Configure Claude Desktop

Open Claude Desktop → Settings → Developer → Edit Config.

This opens `~/Library/Application Support/Claude/claude_desktop_config.json`. Add the `mcpServers` block:

```json
{
  "mcpServers": {
    "job-tracker": {
      "command": "/path/to/JobsApplicationTracker/mcp-server/.venv/bin/python",
      "args": ["/path/to/JobsApplicationTracker/mcp-server/server.py"],
      "env": {
        "JOB_TRACKER_BASE_URL": "http://localhost:8080"
      }
    }
  }
}
```

Replace `/path/to/JobsApplicationTracker` with the actual path on your machine. To get the exact paths:

```bash
cd mcp-server
echo "command: $(pwd)/.venv/bin/python"
echo "script:  $(pwd)/server.py"
```

### 3. Restart Claude Desktop

Fully quit (Cmd+Q) and reopen Claude Desktop. It launches the MCP server process on startup.

### 4. Verify

In a new Claude Desktop conversation, look for the **hammer icon** (🔨) in the bottom-left of the input bar — that means MCP tools loaded successfully. Click it to see all 7 tools listed.

Try asking: *"What jobs am I tracking?"*

If the tools don't appear, check **Claude Desktop → Settings → Developer** for error logs.

## Tools

| Tool | What it does |
|---|---|
| `create_job` | Add a new job application |
| `list_jobs` | See all tracked jobs |
| `get_job` | Get details for one job by ID |
| `update_job` | Change status or notes |
| `delete_job` | Remove a job (soft delete) |
| `analyze_and_wait` | Run AI scoring and return fit score + recommendations |
| `get_master_resume` | See the resume currently used for scoring |

## Example conversations

> "Add a job at Anthropic for Staff Engineer. Here's the JD: ..."

> "What jobs am I currently interviewing for?"

> "Mark the Stripe job as rejected."

> "Score my resume against the Anthropic job."

> "What resume do I have on file?"

## Configuration

| Variable | Default | Description |
|---|---|---|
| `JOB_TRACKER_BASE_URL` | `http://localhost:8080` | Base URL of the Spring Boot API |

## Troubleshooting

**Tools don't appear in Claude Desktop**
- Make sure Claude Desktop was fully quit (Cmd+Q) and reopened after editing the config
- Check that the paths in the config are correct and absolute
- Verify the server starts without errors: `source .venv/bin/activate && python server.py` (it will hang waiting for stdin — that's correct, press Ctrl+C to exit)

**"API error 404" when calling a tool**
- Make sure the Spring Boot API is running (`docker-compose up` or `./gradlew bootRun`)
- Verify `JOB_TRACKER_BASE_URL` in the config points to the right host/port

**`analyze_and_wait` times out**
- The AI pipeline calls OpenRouter — check `OPENROUTER_API_KEY` is set in your Spring Boot environment
- Make sure the job has `jd_text` stored (pass it at creation time)
- A master resume must be uploaded first (`POST /api/v1/resume`)
