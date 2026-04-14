package com.jobtracker.service;

import com.jobtracker.dto.ResumeResponse;
import com.jobtracker.exception.AgentException;
import com.jobtracker.exception.JobNotFoundException;
import com.jobtracker.exception.ResumeNotFoundException;
import com.jobtracker.model.Resume;
import com.jobtracker.repository.JobRepository;
import com.jobtracker.repository.ResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class ResumeService {

    // Hardcoded fallback — used when no resume has been uploaded.
    static final String RESUME_FALLBACK = """
            Senior Software Engineer with 8 years of experience.
            Strong in: Java, Spring Boot, PostgreSQL, REST APIs, microservices, Kubernetes.
            Some experience with: Python, AWS, Kafka.
            Domain: backend systems, distributed systems, payments.
            """;

    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;

    public ResumeService(ResumeRepository resumeRepository, JobRepository jobRepository) {
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public ResumeResponse uploadMasterResume(MultipartFile file) {
        validateFileType(file);
        String contentText = extractText(file);
        validateContentText(contentText);

        Resume resume = new Resume();
        resume.setJobId(null);
        resume.setFileName(file.getOriginalFilename());
        resume.setFileContent(getBytes(file));
        resume.setContentText(contentText);

        Resume saved = resumeRepository.save(resume);
        return toResponse(saved);
    }

    @Transactional
    public ResumeResponse uploadTailoredResume(UUID jobId, MultipartFile file) {
        jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        validateFileType(file);
        String contentText = extractText(file);
        validateContentText(contentText);

        Resume resume = new Resume();
        resume.setJobId(jobId);
        resume.setFileName(file.getOriginalFilename());
        resume.setFileContent(getBytes(file));
        resume.setContentText(contentText);

        Resume saved = resumeRepository.save(resume);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ResumeResponse getCurrentMasterResume() {
        return resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc()
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ResumeResponse getTailoredResume(UUID jobId) {
        jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        return resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Resume downloadMasterResume() {
        return resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc()
                .orElseThrow(() -> new ResumeNotFoundException("No master resume found"));
    }

    @Transactional(readOnly = true)
    public Resume downloadTailoredResume(UUID jobId) {
        jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        return resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId)
                .orElseThrow(() -> new ResumeNotFoundException("No tailored resume found for job " + jobId));
    }

    @Transactional(readOnly = true)
    public String getResumeTextForScoring(UUID jobId) {
        return resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId)
                .map(Resume::getContentText)
                .orElseGet(() -> resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc()
                        .map(Resume::getContentText)
                        .orElse(RESUME_FALLBACK));
    }

    private void validateFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType) && !"text/plain".equals(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE");
        }
    }

    private void validateContentText(String contentText) {
        if (contentText == null || contentText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_RESUME_TEXT");
        }
    }

    private String extractText(MultipartFile file) {
        String contentType = file.getContentType();
        if ("application/pdf".equals(contentType)) {
            try (PDDocument document = Loader.loadPDF(getBytes(file))) {
                return new PDFTextStripper().getText(document);
            } catch (IOException e) {
                throw new AgentException("Failed to extract text from PDF", e);
            }
        } else {
            return new String(getBytes(file), StandardCharsets.UTF_8);
        }
    }

    private byte[] getBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new AgentException("Failed to read file bytes", e);
        }
    }

    private ResumeResponse toResponse(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getJobId(),
                resume.getFileName(),
                resume.getContentText(),
                resume.getCreatedAt());
    }
}
