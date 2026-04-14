"""
MCP server for the Job Application Tracker.

Exposes the REST API as Claude tools so Claude Desktop can manage
job applications conversationally. Each tool is a thin wrapper that
makes an HTTP request to the Spring Boot API and returns the result.

Transport: stdio (Claude Desktop launches this process and talks to it
via stdin/stdout — no ports or HTTP server needed on our side).

Configuration:
    JOB_TRACKER_BASE_URL  Base URL of the Spring Boot API.
                          Defaults to http://localhost:8080
"""

import json
import os
import time

import httpx
from mcp.server.fastmcp import FastMCP

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------

BASE_URL = os.environ.get("JOB_TRACKER_BASE_URL", "http://localhost:8080")

# httpx.Client is a synchronous HTTP client — equivalent to Spring's RestClient.
# base_url means every request below can use a path like "/api/v1/jobs" directly.
# timeout=30 gives the Spring Boot API up to 30 seconds to respond.
client = httpx.Client(base_url=BASE_URL, timeout=30)

# FastMCP is the server object. The name "job-tracker" is what appears in
# Claude Desktop's tool list. Every @mcp.tool() below registers a tool on it.
mcp = FastMCP("job-tracker")


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def _raise_for_status(response: httpx.Response) -> str:
    """Return the response body as a string, or raise with the error body."""
    if response.is_error:
        raise ValueError(f"API error {response.status_code}: {response.text}")
    return response.text


# ---------------------------------------------------------------------------
# Tools
# ---------------------------------------------------------------------------

@mcp.tool()
def create_job(
    company: str,
    role: str,
    jd_text: str = None,
    jd_url: str = None,
    status: str = None,
) -> str:
    """
    Create a new job application in the tracker.

    Use this when the user wants to add, track, or save a new job.
    'jd_text' is the raw job description text — include it whenever the user
    provides or pastes a job description, as it enables AI scoring later.
    'jd_url' is the link to the original job posting (optional).
    'status' defaults to UNDETERMINED if omitted.

    Valid statuses: UNDETERMINED, NOT_A_FIT, APPLIED, SCREENING,
    INTERVIEWING, OFFER_RECEIVED, OFFER_ACCEPTED, OFFER_DECLINED,
    REJECTED, WITHDRAWN, GHOSTED.

    Returns the created job as JSON including its ID.
    """
    payload = {"company": company, "role": role}
    if jd_text:
        payload["jdText"] = jd_text
    if jd_url:
        payload["jdUrl"] = jd_url
    if status:
        payload["status"] = status
    response = client.post("/api/v1/jobs", json=payload)
    return _raise_for_status(response)


@mcp.tool()
def list_jobs() -> str:
    """
    List all active (non-deleted) job applications.

    Use this to give the user an overview of their tracked jobs,
    or when you need to find a job's ID before calling another tool.
    Returns a JSON array of job objects, each with id, company, role,
    status, fitScore, appliedAt, notes, and jdUrl.
    """
    response = client.get("/api/v1/jobs")
    return _raise_for_status(response)


@mcp.tool()
def get_job(job_id: str) -> str:
    """
    Get the full details of a single job application by its UUID.

    Use this when the user asks about a specific job and you already
    have its ID (e.g. from a previous list_jobs call).
    Returns a single job object as JSON.
    """
    response = client.get(f"/api/v1/jobs/{job_id}")
    return _raise_for_status(response)


@mcp.tool()
def update_job(job_id: str, status: str = None, notes: str = None) -> str:
    """
    Update a job application's status or notes.

    Use this when the user wants to change the status of a job
    (e.g. "mark as rejected", "I got an offer from Stripe") or add/update notes.
    At least one of 'status' or 'notes' must be provided.

    Valid statuses: UNDETERMINED, NOT_A_FIT, APPLIED, SCREENING,
    INTERVIEWING, OFFER_RECEIVED, OFFER_ACCEPTED, OFFER_DECLINED,
    REJECTED, WITHDRAWN, GHOSTED.

    Setting 'notes' to an empty string clears existing notes.
    Returns the updated job as JSON.
    """
    payload = {}
    if status is not None:
        payload["status"] = status
    if notes is not None:
        payload["notes"] = notes
    response = client.patch(f"/api/v1/jobs/{job_id}", json=payload)
    return _raise_for_status(response)


@mcp.tool()
def delete_job(job_id: str) -> str:
    """
    Soft-delete a job application (it will no longer appear in list_jobs).

    Use this when the user wants to remove a job they're no longer interested
    in or that was added by mistake. The record is not permanently deleted —
    it is hidden by setting a deleted_at timestamp.
    Returns a confirmation message.
    """
    response = client.delete(f"/api/v1/jobs/{job_id}")
    if response.status_code == 204:
        return json.dumps({"message": f"Job {job_id} deleted successfully."})
    return _raise_for_status(response)


@mcp.tool()
def analyze_and_wait(job_id: str) -> str:
    """
    Run AI analysis on a job's description and return the fit score and recommendations.

    Use this when the user wants to know how well their resume matches a job,
    or asks for scoring, recommendations, or analysis of a specific job.
    The job must have jd_text stored (added at creation or via update).

    This triggers the async AI pipeline (JD parsing → resume scoring) and
    waits up to 60 seconds for the result, polling every 3 seconds.

    Returns the fit score (0-100) and a list of recommendations as JSON.
    If the analysis does not complete within 60 seconds, returns a timeout message.
    """
    # Step 1: trigger the async pipeline — API returns 202 Accepted immediately
    trigger = client.post(f"/api/v1/jobs/{job_id}/analyze")
    _raise_for_status(trigger)

    # Step 2: poll GET /score until a result appears or we time out.
    # The AI pipeline runs on a background thread in Spring Boot, so it takes
    # a few seconds. We check every 3 seconds for up to 60 seconds total.
    deadline = time.time() + 60
    while time.time() < deadline:
        time.sleep(3)
        score_response = client.get(f"/api/v1/jobs/{job_id}/score")
        if score_response.status_code == 200:
            return score_response.text
        # 404 means no score yet — keep polling

    return json.dumps({
        "error": "TIMEOUT",
        "message": "Analysis did not complete within 60 seconds. Try GET /score manually later.",
    })


@mcp.tool()
def get_master_resume() -> str:
    """
    Get the current master resume on file (metadata and extracted text).

    Use this when the user asks what resume is being used for scoring,
    wants to verify their resume is uploaded, or wants to see their resume content.
    Returns resume metadata and the full extracted text content.
    If no master resume has been uploaded yet, returns a 404 error.
    """
    response = client.get("/api/v1/resume")
    return _raise_for_status(response)


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    # mcp.run() starts the stdio transport loop.
    # Claude Desktop writes JSON-RPC messages to our stdin,
    # we process them and write responses to stdout.
    # This process runs until Claude Desktop shuts it down.
    mcp.run()
