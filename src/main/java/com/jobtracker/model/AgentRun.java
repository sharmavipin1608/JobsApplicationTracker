package com.jobtracker.model;

import com.jobtracker.enums.AgentRunStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_runs")
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String agentName;

    private String modelUsed;

    @Column(columnDefinition = "TEXT")
    private String inputText;

    @Column(columnDefinition = "TEXT")
    private String outputText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentRunStatus status = AgentRunStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public String getAgentName() { return agentName; }
    public String getModelUsed() { return modelUsed; }
    public String getInputText() { return inputText; }
    public String getOutputText() { return outputText; }
    public AgentRunStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public void setInputText(String inputText) { this.inputText = inputText; }
    public void setOutputText(String outputText) { this.outputText = outputText; }
    public void setStatus(AgentRunStatus status) { this.status = status; }
}
