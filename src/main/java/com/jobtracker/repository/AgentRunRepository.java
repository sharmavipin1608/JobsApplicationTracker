package com.jobtracker.repository;

import com.jobtracker.model.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    List<AgentRun> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}
