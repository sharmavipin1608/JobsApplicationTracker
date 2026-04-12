package com.jobtracker.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scores")
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    private Integer fitScore;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public Integer getFitScore() { return fitScore; }
    public String getRecommendations() { return recommendations; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public void setFitScore(Integer fitScore) { this.fitScore = fitScore; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }
}
