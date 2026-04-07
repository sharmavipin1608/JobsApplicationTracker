package com.jobtracker.service;

import com.jobtracker.dto.CreateJobRequest;
import com.jobtracker.dto.JobResponse;
import com.jobtracker.enums.JobStatus;
import com.jobtracker.mapper.JobMapper;
import com.jobtracker.model.Job;
import com.jobtracker.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobMapper jobMapper;

    public JobService(JobRepository jobRepository, JobMapper jobMapper) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
    }

    public JobResponse createJob(CreateJobRequest request) {
        Job job = jobMapper.toEntity(request);
        JobStatus status = job.getStatus();
        if (job.getAppliedAt() == null && status != null && !status.isPreApplication()) {
            job.setAppliedAt(LocalDateTime.now());
        }
        Job saved = jobRepository.save(job);
        return jobMapper.toResponse(saved);
    }
}
