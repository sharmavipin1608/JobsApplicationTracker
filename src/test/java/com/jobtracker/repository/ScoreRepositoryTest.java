package com.jobtracker.repository;

import com.jobtracker.enums.JobStatus;
import com.jobtracker.model.Job;
import com.jobtracker.model.Score;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScoreRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private ScoreRepository scoreRepository;
    @Autowired private JobRepository jobRepository;

    private Job persistJob() {
        Job job = new Job();
        job.setCompany("Acme");
        job.setRole("Engineer");
        job.setStatus(JobStatus.UNDETERMINED);
        return jobRepository.save(job);
    }

    @Test
    void shouldSaveScore_withCreatedAtPopulated() {
        Job job = persistJob();
        Score score = new Score();
        score.setJobId(job.getId());
        score.setFitScore(82);
        score.setRecommendations("[\"Highlight GCP experience\"]");

        Score saved = scoreRepository.save(score);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getFitScore()).isEqualTo(82);
    }

    @Test
    void shouldFindLatestScore_whenMultipleScoresExist() {
        Job job = persistJob();

        Score older = new Score();
        older.setJobId(job.getId());
        older.setFitScore(60);
        scoreRepository.saveAndFlush(older);

        Score newer = new Score();
        newer.setJobId(job.getId());
        newer.setFitScore(85);
        scoreRepository.saveAndFlush(newer);

        Optional<Score> latest = scoreRepository.findFirstByJobIdOrderByCreatedAtDesc(job.getId());

        assertThat(latest).isPresent();
        assertThat(latest.get().getFitScore()).isEqualTo(85);
    }

    @Test
    void shouldReturnEmpty_whenNoScoreExistsForJob() {
        Job job = persistJob();

        Optional<Score> result = scoreRepository.findFirstByJobIdOrderByCreatedAtDesc(job.getId());

        assertThat(result).isEmpty();
    }
}
