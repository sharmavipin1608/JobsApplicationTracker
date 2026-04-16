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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private JobMapper jobMapper;

    @Mock
    private OrchestratorService orchestratorService;

    @InjectMocks
    private JobService jobService;

    private JobResponse sampleResponse(JobStatus status, LocalDateTime appliedAt) {
        return new JobResponse(
                UUID.randomUUID(), "Acme Corp", "Senior Engineer",
                status, appliedAt, null, null, null, null, LocalDateTime.now(), null
        );
    }

    // ---------- create ----------

    @Test
    void shouldReturnJobResponse_whenValidRequest() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, null, null, JobStatus.APPLIED, LocalDateTime.now()
        );
        Job mappedJob = new Job();
        mappedJob.setStatus(JobStatus.APPLIED);
        mappedJob.setAppliedAt(LocalDateTime.now());
        Job savedJob = new Job();
        JobResponse expectedResponse = sampleResponse(JobStatus.APPLIED, LocalDateTime.now());

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(expectedResponse);

        JobResponse result = jobService.createJob(request);

        assertThat(result).isEqualTo(expectedResponse);
        verify(orchestratorService, never()).analyze(any(), any());
    }

    @Test
    void shouldAutoTriggerAnalyze_whenJdTextIsPresent() {
        UUID jobId = UUID.randomUUID();
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", "We need a Java engineer", null, null, JobStatus.UNDETERMINED, null
        );
        Job mappedJob = new Job();
        mappedJob.setStatus(JobStatus.UNDETERMINED);
        mappedJob.setJdText("We need a Java engineer");
        Job savedJob = new Job();
        ReflectionTestUtils.setField(savedJob, "id", jobId);
        savedJob.setJdText("We need a Java engineer");

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(sampleResponse(JobStatus.UNDETERMINED, null));

        jobService.createJob(request);

        verify(orchestratorService).analyze(eq(jobId), eq("We need a Java engineer"));
    }

    @Test
    void shouldDefaultAppliedAt_whenStatusIsPostApplicationAndAppliedAtIsNull() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, null, null, JobStatus.APPLIED, null
        );
        Job mappedJob = new Job();
        mappedJob.setStatus(JobStatus.APPLIED);
        Job savedJob = new Job();

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(sampleResponse(JobStatus.APPLIED, LocalDateTime.now()));

        jobService.createJob(request);

        assertThat(mappedJob.getAppliedAt()).isNotNull();
    }

    @Test
    void shouldNotDefaultAppliedAt_whenStatusIsPreApplication() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, null, null, JobStatus.UNDETERMINED, null
        );
        Job mappedJob = new Job();
        mappedJob.setStatus(JobStatus.UNDETERMINED);
        Job savedJob = new Job();

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(sampleResponse(JobStatus.UNDETERMINED, null));

        jobService.createJob(request);

        assertThat(mappedJob.getAppliedAt()).isNull();
    }

    // ---------- findAll ----------

    @Test
    void shouldReturnAllJobs_asResponses() {
        Job job1 = new Job();
        Job job2 = new Job();
        when(jobRepository.findAll()).thenReturn(List.of(job1, job2));
        when(jobMapper.toResponse(job1)).thenReturn(sampleResponse(JobStatus.APPLIED, null));
        when(jobMapper.toResponse(job2)).thenReturn(sampleResponse(JobStatus.UNDETERMINED, null));

        List<JobResponse> result = jobService.findAll();

        assertThat(result).hasSize(2);
    }

    // ---------- findById ----------

    @Test
    void shouldReturnJob_whenIdExists() {
        UUID id = UUID.randomUUID();
        Job job = new Job();
        JobResponse expected = sampleResponse(JobStatus.APPLIED, null);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobMapper.toResponse(job)).thenReturn(expected);

        assertThat(jobService.findById(id)).isEqualTo(expected);
    }

    @Test
    void shouldThrowNotFound_whenIdDoesNotExist_onFindById() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.findById(id))
                .isInstanceOf(JobNotFoundException.class);
    }

    // ---------- updateJob ----------

    @Test
    void shouldUpdateStatusAndNotes_whenBothProvided() {
        UUID id = UUID.randomUUID();
        Job existing = new Job();
        existing.setStatus(JobStatus.UNDETERMINED);
        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));
        when(jobRepository.save(existing)).thenReturn(existing);
        when(jobMapper.toResponse(existing)).thenReturn(sampleResponse(JobStatus.APPLIED, LocalDateTime.now()));

        UpdateJobRequest req = new UpdateJobRequest(JobStatus.APPLIED, "Spoke to recruiter", null);
        jobService.updateJob(id, req);

        assertThat(existing.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(existing.getNotes()).isEqualTo("Spoke to recruiter");
        assertThat(existing.getAppliedAt()).isNotNull(); // defaulted because new status is post-application
    }

    @Test
    void shouldClearNotes_whenEmptyStringProvided() {
        UUID id = UUID.randomUUID();
        Job existing = new Job();
        existing.setNotes("old notes");
        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));
        when(jobRepository.save(existing)).thenReturn(existing);
        when(jobMapper.toResponse(existing)).thenReturn(sampleResponse(JobStatus.UNDETERMINED, null));

        jobService.updateJob(id, new UpdateJobRequest(null, "", null));

        assertThat(existing.getNotes()).isNull();
    }

    @Test
    void shouldLeaveFieldsUnchanged_whenNullInPatch() {
        UUID id = UUID.randomUUID();
        Job existing = new Job();
        existing.setStatus(JobStatus.APPLIED);
        existing.setNotes("keep me");
        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));
        when(jobRepository.save(existing)).thenReturn(existing);
        when(jobMapper.toResponse(existing)).thenReturn(sampleResponse(JobStatus.APPLIED, null));

        jobService.updateJob(id, new UpdateJobRequest(null, null, null));

        assertThat(existing.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(existing.getNotes()).isEqualTo("keep me");
    }

    @Test
    void shouldNotOverwriteAppliedAt_whenUpdatingStatusButAppliedAtAlreadySet() {
        UUID id = UUID.randomUUID();
        Job existing = new Job();
        existing.setStatus(JobStatus.APPLIED);
        LocalDateTime original = LocalDateTime.of(2025, 1, 1, 0, 0);
        existing.setAppliedAt(original);
        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));
        when(jobRepository.save(existing)).thenReturn(existing);
        when(jobMapper.toResponse(existing)).thenReturn(sampleResponse(JobStatus.INTERVIEWING, original));

        jobService.updateJob(id, new UpdateJobRequest(JobStatus.INTERVIEWING, null, null));

        assertThat(existing.getAppliedAt()).isEqualTo(original);
    }

    @Test
    void shouldThrowNotFound_whenIdDoesNotExist_onUpdate() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.updateJob(id, new UpdateJobRequest(JobStatus.APPLIED, null, null)))
                .isInstanceOf(JobNotFoundException.class);
        verify(jobRepository, never()).save(any());
    }

    // ---------- deleteJob ----------

    @Test
    void shouldDeleteJob_whenIdExists() {
        UUID id = UUID.randomUUID();
        Job existing = new Job();
        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));

        jobService.deleteJob(id);

        verify(jobRepository).delete(existing);
    }

    @Test
    void shouldThrowNotFound_whenIdDoesNotExist_onDelete() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.deleteJob(id))
                .isInstanceOf(JobNotFoundException.class);
        verify(jobRepository, never()).delete(any(Job.class));
    }

    // ---------- getJdText ----------

    @Test
    void shouldReturnJdText_whenJobExists() {
        UUID id = UUID.randomUUID();
        Job job = new Job();
        job.setJdText("Some JD text");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        assertThat(jobService.getJdText(id)).isEqualTo("Some JD text");
    }

    @Test
    void shouldThrowNotFound_whenJobDoesNotExist_onGetJdText() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJdText(id))
                .isInstanceOf(JobNotFoundException.class);
    }

    // ---------- jdUrl ----------

    @Test
    void shouldCreateJob_withJdUrl() {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, "https://example.com/job", null, JobStatus.UNDETERMINED, null
        );
        Job mappedJob = new Job();
        mappedJob.setStatus(JobStatus.UNDETERMINED);
        mappedJob.setJdUrl("https://example.com/job");
        Job savedJob = new Job();

        when(jobMapper.toEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(savedJob);
        when(jobMapper.toResponse(savedJob)).thenReturn(sampleResponse(JobStatus.UNDETERMINED, null));

        jobService.createJob(request);

        assertThat(mappedJob.getJdUrl()).isEqualTo("https://example.com/job");
    }

    @Test
    void shouldPatchJdUrl() {
        UUID id = UUID.randomUUID();
        Job existing = new Job();
        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));
        when(jobRepository.save(existing)).thenReturn(existing);
        when(jobMapper.toResponse(existing)).thenReturn(sampleResponse(JobStatus.UNDETERMINED, null));

        jobService.updateJob(id, new UpdateJobRequest(null, null, "https://example.com/job"));

        assertThat(existing.getJdUrl()).isEqualTo("https://example.com/job");
    }

    @Test
    void shouldClearJdUrl_whenEmptyString() {
        UUID id = UUID.randomUUID();
        Job existing = new Job();
        existing.setJdUrl("https://old-url.com");
        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));
        when(jobRepository.save(existing)).thenReturn(existing);
        when(jobMapper.toResponse(existing)).thenReturn(sampleResponse(JobStatus.UNDETERMINED, null));

        jobService.updateJob(id, new UpdateJobRequest(null, null, ""));

        assertThat(existing.getJdUrl()).isNull();
    }

    // ---------- findLatestScore ----------

    @Test
    void shouldReturnScoreResponse_whenScoreExists() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        Score score = new Score();
        score.setJobId(jobId);
        score.setFitScore(82);
        score.setRecommendations("[\"Add GCP\"]");
        when(scoreRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId))
                .thenReturn(Optional.of(score));

        ScoreResponse result = jobService.findLatestScore(jobId);

        assertThat(result).isNotNull();
        assertThat(result.fitScore()).isEqualTo(82);
    }

    @Test
    void shouldReturnNull_whenNoScoreExists() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(new Job()));
        when(scoreRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId))
                .thenReturn(Optional.empty());

        assertThat(jobService.findLatestScore(jobId)).isNull();
    }

    @Test
    void shouldThrowNotFound_whenJobDoesNotExist_onFindLatestScore() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.findLatestScore(jobId))
                .isInstanceOf(JobNotFoundException.class);
    }
}
