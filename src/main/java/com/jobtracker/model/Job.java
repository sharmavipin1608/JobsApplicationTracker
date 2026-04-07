package com.jobtracker.model;

import com.jobtracker.enums.JobStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    private String jdText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    private LocalDateTime appliedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters for fields the service needs to read
    public UUID getId() { return id; }
    public String getCompany() { return company; }
    public String getRole() { return role; }
    public JobStatus getStatus() { return status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters for fields the service needs to set
    public void setCompany(String company) { this.company = company; }
    public void setRole(String role) { this.role = role; }
    public void setJdText(String jdText) { this.jdText = jdText; }
    public void setStatus(JobStatus status) { this.status = status; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
}
