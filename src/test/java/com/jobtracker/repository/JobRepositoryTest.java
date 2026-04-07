package com.jobtracker.repository;

import com.jobtracker.enums.JobStatus;
import com.jobtracker.model.Job;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JobRepository jobRepository;

    @Test
    void shouldSaveJob_whenValidInput() {
        Job job = new Job();
        job.setCompany("Acme Corp");
        job.setRole("Senior Software Engineer");
        job.setStatus(JobStatus.UNDETERMINED);

        Job saved = jobRepository.save(job);

        Optional<Job> found = jobRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCompany()).isEqualTo("Acme Corp");
        assertThat(found.get().getRole()).isEqualTo("Senior Software Engineer");
        assertThat(found.get().getStatus()).isEqualTo(JobStatus.UNDETERMINED);
    }

    @Test
    void shouldAutoPopulateTimestamps_whenJobIsSaved() {
        Job job = new Job();
        job.setCompany("Acme Corp");
        job.setRole("Senior Software Engineer");
        job.setStatus(JobStatus.UNDETERMINED);

        Job saved = jobRepository.save(job);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isBefore(LocalDateTime.now());
    }

    @Test
    void shouldSaveJob_whenOptionalFieldsAreNull() {
        Job job = new Job();
        job.setCompany("Acme Corp");
        job.setRole("Senior Software Engineer");
        job.setStatus(JobStatus.APPLIED);
        // jdText and appliedAt intentionally omitted

        Job saved = jobRepository.save(job);

        Optional<Job> found = jobRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAppliedAt()).isNull();
    }
}
