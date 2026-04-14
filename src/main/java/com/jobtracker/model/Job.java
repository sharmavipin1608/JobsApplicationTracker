package com.jobtracker.model;

import com.jobtracker.enums.JobStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@SQLDelete(sql = "UPDATE jobs SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    private String jdText;

    @Column(name = "jd_url")
    private String jdUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    private LocalDateTime appliedAt;

    private String notes;

    private Integer fitScore;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime deletedAt;

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
    public String getJdText() { return jdText; }
    public String getJdUrl() { return jdUrl; }
    public JobStatus getStatus() { return status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public String getNotes() { return notes; }
    public Integer getFitScore() { return fitScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }

    // Setters for fields the service needs to set
    public void setCompany(String company) { this.company = company; }
    public void setRole(String role) { this.role = role; }
    public void setJdText(String jdText) { this.jdText = jdText; }
    public void setJdUrl(String jdUrl) { this.jdUrl = jdUrl; }
    public void setStatus(JobStatus status) { this.status = status; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setFitScore(Integer fitScore) { this.fitScore = fitScore; }
}
