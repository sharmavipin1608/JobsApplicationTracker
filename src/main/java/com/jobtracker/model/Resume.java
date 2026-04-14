package com.jobtracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_content", nullable = false)
    @Basic(fetch = FetchType.LAZY)
    private byte[] fileContent;

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public String getFileName() { return fileName; }
    public byte[] getFileContent() { return fileContent; }
    public String getContentText() { return contentText; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileContent(byte[] fileContent) { this.fileContent = fileContent; }
    public void setContentText(String contentText) { this.contentText = contentText; }
}
