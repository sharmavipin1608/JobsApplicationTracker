package com.jobtracker.service;

import com.jobtracker.dto.CreateJobRequest;
import com.jobtracker.dto.JobResponse;
import com.jobtracker.dto.ScoreResponse;
import com.jobtracker.dto.UpdateJobRequest;
import com.jobtracker.enums.JobStatus;
import com.jobtracker.exception.JobNotFoundException;
import com.jobtracker.mapper.JobMapper;
import com.jobtracker.model.Job;
import com.jobtracker.model.Score;
import com.jobtracker.repository.JobRepository;
import com.jobtracker.repository.ScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final ScoreRepository scoreRepository;
    private final JobMapper jobMapper;
    private final OrchestratorService orchestratorService;

    public JobService(JobRepository jobRepository, ScoreRepository scoreRepository,
                      JobMapper jobMapper, OrchestratorService orchestratorService) {
        this.jobRepository = jobRepository;
        this.scoreRepository = scoreRepository;
        this.jobMapper = jobMapper;
        this.orchestratorService = orchestratorService;
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request) {
        Job job = jobMapper.toEntity(request);
        applyAppliedAtDefault(job);
        Job saved = jobRepository.save(job);
        if (saved.getJdText() != null && !saved.getJdText().isBlank()) {
            orchestratorService.analyze(saved.getId(), saved.getJdText());
        }
        return jobMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> findAll() {
        return jobRepository.findAll().stream()
                .map(jobMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobResponse findById(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
        return jobMapper.toResponse(job);
    }

    @Transactional
    public JobResponse updateJob(UUID id, UpdateJobRequest request) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        if (request.status() != null) {
            job.setStatus(request.status());
            applyAppliedAtDefault(job);
        }
        if (request.notes() != null) {
            // Empty string clears the notes (stored as null)
            job.setNotes(request.notes().isEmpty() ? null : request.notes());
        }
        if (request.jdUrl() != null) {
            job.setJdUrl(request.jdUrl().isEmpty() ? null : request.jdUrl());
        }

        Job saved = jobRepository.save(job);
        return jobMapper.toResponse(saved);
    }

    @Transactional
    public void deleteJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
        jobRepository.delete(job); // soft delete via @SQLDelete
    }

    @Transactional(readOnly = true)
    public String getJdText(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
        return job.getJdText();
    }

    @Transactional(readOnly = true)
    public ScoreResponse findLatestScore(UUID jobId) {
        // Verify job exists first (throws 404 if not found or soft-deleted)
        jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return scoreRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId)
                .map(score -> new ScoreResponse(
                        score.getId(),
                        score.getJobId(),
                        score.getFitScore(),
                        score.getRecommendations(),
                        score.getCreatedAt()
                ))
                .orElse(null);
    }

    private void applyAppliedAtDefault(Job job) {
        JobStatus status = job.getStatus();
        if (job.getAppliedAt() == null && status != null && !status.isPreApplication()) {
            job.setAppliedAt(LocalDateTime.now());
        }
    }
}
