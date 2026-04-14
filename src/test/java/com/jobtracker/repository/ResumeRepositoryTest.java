package com.jobtracker.repository;

import com.jobtracker.enums.JobStatus;
import com.jobtracker.model.Job;
import com.jobtracker.model.Resume;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResumeRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private ResumeRepository resumeRepository;
    @Autowired private JobRepository jobRepository;

    private Job persistJob() {
        Job job = new Job();
        job.setCompany("Acme");
        job.setRole("Engineer");
        job.setStatus(JobStatus.UNDETERMINED);
        return jobRepository.save(job);
    }

    private Resume buildResume(UUID jobId) {
        Resume resume = new Resume();
        resume.setJobId(jobId);
        resume.setFileName("resume.txt");
        resume.setFileContent("content".getBytes());
        resume.setContentText("Some resume text");
        return resume;
    }

    @Test
    void shouldSaveMasterResume_withNullJobId() {
        Resume resume = buildResume(null);
        Resume saved = resumeRepository.save(resume);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getJobId()).isNull();
        assertThat(saved.getFileName()).isEqualTo("resume.txt");
        assertThat(saved.getContentText()).isEqualTo("Some resume text");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindLatestMasterResume_whenMultipleExist() {
        resumeRepository.saveAndFlush(buildResume(null));

        Resume newer = buildResume(null);
        newer.setContentText("Newer resume text");
        resumeRepository.saveAndFlush(newer);

        Optional<Resume> latest = resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc();

        assertThat(latest).isPresent();
        assertThat(latest.get().getContentText()).isEqualTo("Newer resume text");
    }

    @Test
    void shouldReturnEmpty_whenNoMasterResumeExists() {
        Optional<Resume> result = resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindLatestTailoredResume_forSpecificJob() {
        Job job = persistJob();

        Resume older = buildResume(job.getId());
        older.setContentText("Older tailored");
        resumeRepository.saveAndFlush(older);

        Resume newer = buildResume(job.getId());
        newer.setContentText("Newer tailored");
        resumeRepository.saveAndFlush(newer);

        Optional<Resume> latest = resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(job.getId());

        assertThat(latest).isPresent();
        assertThat(latest.get().getContentText()).isEqualTo("Newer tailored");
    }

    @Test
    void shouldNotReturnMasterResume_whenQueryingTailored() {
        resumeRepository.saveAndFlush(buildResume(null));

        Optional<Resume> result = resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
