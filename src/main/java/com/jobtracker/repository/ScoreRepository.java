package com.jobtracker.repository;

import com.jobtracker.model.Score;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoreRepository extends JpaRepository<Score, UUID> {

    Optional<Score> findFirstByJobIdOrderByCreatedAtDesc(UUID jobId);
}
