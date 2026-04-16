package com.jobtracker.mcp;

import com.jobtracker.dto.CreateJobRequest;
import com.jobtracker.dto.JobResponse;
import com.jobtracker.dto.ResumeResponse;
import com.jobtracker.dto.ScoreResponse;
import com.jobtracker.dto.UpdateJobRequest;
import com.jobtracker.enums.JobStatus;
import com.jobtracker.model.Score;
import com.jobtracker.service.JobService;
import com.jobtracker.service.OrchestratorService;
import com.jobtracker.service.ResumeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class JobTrackerMcpTools {

    private final JobService jobService;
    private final ResumeService resumeService;
    private final OrchestratorService orchestratorService;

    public JobTrackerMcpTools(JobService jobService, ResumeService resumeService,
                               OrchestratorService orchestratorService) {
        this.jobService = jobService;
        this.resumeService = resumeService;
        this.orchestratorService = orchestratorService;
    }

    @Tool(description = "Create a new job application. Pass jdText to trigger async AI fit scoring.")
    public JobResponse createJob(
            @ToolParam(description = "Company name") String company,
            @ToolParam(description = "Job role or title") String role,
            @ToolParam(required = false, description = "Full job description text — triggers AI scoring when provided") String jdText,
            @ToolParam(required = false, description = "URL of the job posting") String jdUrl,
            @ToolParam(required = false, description = "Initial status. One of: UNDETERMINED, APPLIED, SCREENING, INTERVIEWING, OFFER_RECEIVED, OFFER_ACCEPTED, OFFER_DECLINED, REJECTED, WITHDRAWN, GHOSTED, NOT_A_FIT. Defaults to UNDETERMINED.") String status) {
        JobStatus jobStatus = status != null ? JobStatus.valueOf(status) : null;
        return jobService.createJob(new CreateJobRequest(company, role, jdText, jdUrl, jobStatus, null));
    }

    @Tool(description = "List all active (non-deleted) job applications")
    public List<JobResponse> listJobs() {
        return jobService.findAll();
    }

    @Tool(description = "Get a single job application by its UUID")
    public JobResponse getJob(
            @ToolParam(description = "Job UUID") String jobId) {
        return jobService.findById(UUID.fromString(jobId));
    }

    @Tool(description = "Update a job application's status, notes, or JD URL. Pass only the fields to change.")
    public JobResponse updateJob(
            @ToolParam(description = "Job UUID") String jobId,
            @ToolParam(required = false, description = "New status. One of: UNDETERMINED, APPLIED, SCREENING, INTERVIEWING, OFFER_RECEIVED, OFFER_ACCEPTED, OFFER_DECLINED, REJECTED, WITHDRAWN, GHOSTED, NOT_A_FIT") String status,
            @ToolParam(required = false, description = "Notes to set. Empty string clears notes.") String notes,
            @ToolParam(required = false, description = "New JD URL. Empty string clears it.") String jdUrl) {
        JobStatus jobStatus = status != null ? JobStatus.valueOf(status) : null;
        return jobService.updateJob(UUID.fromString(jobId), new UpdateJobRequest(jobStatus, notes, jdUrl));
    }

    @Tool(description = "Soft-delete a job application. The record is hidden but not permanently removed.")
    public String deleteJob(
            @ToolParam(description = "Job UUID") String jobId) {
        jobService.deleteJob(UUID.fromString(jobId));
        return "Deleted job " + jobId;
    }

    @Tool(description = "Trigger AI analysis on a job's description and wait for the fit score (blocks up to 60 seconds). The job must have jdText set.")
    public ScoreResponse analyzeAndWait(
            @ToolParam(description = "Job UUID") String jobId) {
        UUID id = UUID.fromString(jobId);
        String jdText = jobService.getJdText(id);
        if (jdText == null || jdText.isBlank()) {
            throw new IllegalArgumentException("Job " + jobId + " has no jdText — set it first before analyzing");
        }
        CompletableFuture<Score> future = orchestratorService.analyze(id, jdText);
        try {
            Score score = future.get(60, TimeUnit.SECONDS);
            return new ScoreResponse(score.getId(), score.getJobId(), score.getFitScore(),
                    score.getRecommendations(), score.getCreatedAt());
        } catch (TimeoutException e) {
            throw new RuntimeException("Analysis timed out after 60 seconds");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Analysis failed: " + cause.getMessage(), cause);
        }
    }

    @Tool(description = "Get the current master resume metadata (filename, extracted text, upload date)")
    public ResumeResponse getMasterResume() {
        ResumeResponse resume = resumeService.getCurrentMasterResume();
        if (resume == null) {
            throw new RuntimeException("No master resume found. Upload one via POST /api/v1/resume");
        }
        return resume;
    }
}
