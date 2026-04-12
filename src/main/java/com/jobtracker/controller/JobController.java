package com.jobtracker.controller;

import com.jobtracker.service.OrchestratorService;
import com.jobtracker.dto.CreateJobRequest;
import com.jobtracker.dto.JobResponse;
import com.jobtracker.dto.ScoreResponse;
import com.jobtracker.dto.UpdateJobRequest;
import com.jobtracker.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final OrchestratorService orchestratorService;

    public JobController(JobService jobService, OrchestratorService orchestratorService) {
        this.jobService = jobService;
        this.orchestratorService = orchestratorService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobResponse response = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<JobResponse> listJobs() {
        return jobService.findAll();
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return jobService.findById(id);
    }

    @PatchMapping("/{id}")
    public JobResponse updateJob(@PathVariable UUID id, @RequestBody UpdateJobRequest request) {
        return jobService.updateJob(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable UUID id) {
        jobService.deleteJob(id);
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<Map<String, String>> analyzeJob(@PathVariable UUID id) {
        String jdText = jobService.getJdText(id);
        if (jdText == null || jdText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MISSING_JD_TEXT",
                    "message", "Job must have jdText to run analysis"
            ));
        }
        orchestratorService.analyze(id, jdText);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Analysis started",
                "jobId", id.toString()
        ));
    }

    @GetMapping("/{id}/score")
    public ScoreResponse getScore(@PathVariable UUID id) {
        return jobService.findLatestScore(id);
    }
}
