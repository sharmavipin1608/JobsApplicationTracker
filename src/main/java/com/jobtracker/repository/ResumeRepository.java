package com.jobtracker.repository;

import com.jobtracker.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    Optional<Resume> findFirstByJobIdIsNullOrderByCreatedAtDesc();

    Optional<Resume> findFirstByJobIdOrderByCreatedAtDesc(UUID jobId);
}
