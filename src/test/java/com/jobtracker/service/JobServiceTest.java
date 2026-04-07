package com.jobtracker.service;

import com.jobtracker.dto.CreateJobRequest;
import com.jobtracker.dto.JobResponse;
import com.jobtracker.enums.JobStatus;
import com.jobtracker.mapper.JobMapper;
import com.jobtracker.model.Job;
import com.jobtracker.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobMapper jobMapper;

    @InjectMocks
    private JobService jobService;

    @Test
    void shouldReturnJobResponse_whenValidRequest() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, JobStatus.APPLIED, LocalDateTime.now()
        );
        Job mappedJob = new Job();
        Job savedJob = new Job();
        JobResponse expectedResponse = new JobResponse(
                UUID.randomUUID(), "Acme Corp", "Senior Engineer",
                JobStatus.APPLIED, null, LocalDateTime.now()
        );

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(expectedResponse);

        JobResponse result = jobService.createJob(request);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    void shouldCallMapperAndRepository_inCorrectOrder() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, null, null
        );
        Job mappedJob = new Job();
        Job savedJob = new Job();
        JobResponse response = new JobResponse(
                UUID.randomUUID(), "Acme Corp", "Senior Engineer",
                JobStatus.UNDETERMINED, null, LocalDateTime.now()
        );

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(response);

        jobService.createJob(request);

        verify(jobMapper).toEntity(request);
        verify(jobRepository).save(mappedJob);
        verify(jobMapper).toResponse(savedJob);
    }

    @Test
    void shouldDefaultAppliedAt_whenStatusIsPostApplicationAndAppliedAtIsNull() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, JobStatus.APPLIED, null
        );
        Job mappedJob = new Job();
        mappedJob.setStatus(JobStatus.APPLIED);
        Job savedJob = new Job();
        JobResponse response = new JobResponse(
                UUID.randomUUID(), "Acme Corp", "Senior Engineer",
                JobStatus.APPLIED, LocalDateTime.now(), LocalDateTime.now()
        );

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(response);

        jobService.createJob(request);

        assertThat(mappedJob.getAppliedAt()).isNotNull();
    }

    @Test
    void shouldNotDefaultAppliedAt_whenStatusIsPreApplication() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, JobStatus.UNDETERMINED, null
        );
        Job mappedJob = new Job();
        mappedJob.setStatus(JobStatus.UNDETERMINED);
        Job savedJob = new Job();
        JobResponse response = new JobResponse(
                UUID.randomUUID(), "Acme Corp", "Senior Engineer",
                JobStatus.UNDETERMINED, null, LocalDateTime.now()
        );

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(response);

        jobService.createJob(request);

        assertThat(mappedJob.getAppliedAt()).isNull();
    }

    @Test
    void shouldCreateJob_whenOptionalFieldsAreNull() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, null, null
        );
        Job mappedJob = new Job();
        Job savedJob = new Job();
        JobResponse response = new JobResponse(
                UUID.randomUUID(), "Acme Corp", "Senior Engineer",
                JobStatus.UNDETERMINED, null, LocalDateTime.now()
        );

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(response);

        JobResponse result = jobService.createJob(request);

        assertThat(result.appliedAt()).isNull();
        assertThat(result.status()).isEqualTo(JobStatus.UNDETERMINED);
    }
}
