package com.jobtracker.controller;

import com.jobtracker.dto.ResumeResponse;
import com.jobtracker.model.Resume;
import com.jobtracker.service.ResumeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/resume")
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse uploadMasterResume(@RequestParam("file") MultipartFile file) {
        return resumeService.uploadMasterResume(file);
    }

    @GetMapping("/resume")
    public ResumeResponse getCurrentMasterResume() {
        return resumeService.getCurrentMasterResume();
    }

    @GetMapping("/resume/download")
    public ResponseEntity<byte[]> downloadMasterResume() {
        Resume resume = resumeService.downloadMasterResume();
        return buildDownloadResponse(resume);
    }

    @PostMapping("/jobs/{id}/resume")
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse uploadTailoredResume(@PathVariable UUID id,
                                                @RequestParam("file") MultipartFile file) {
        return resumeService.uploadTailoredResume(id, file);
    }

    @GetMapping("/jobs/{id}/resume")
    public ResumeResponse getTailoredResume(@PathVariable UUID id) {
        return resumeService.getTailoredResume(id);
    }

    @GetMapping("/jobs/{id}/resume/download")
    public ResponseEntity<byte[]> downloadTailoredResume(@PathVariable UUID id) {
        Resume resume = resumeService.downloadTailoredResume(id);
        return buildDownloadResponse(resume);
    }

    private ResponseEntity<byte[]> buildDownloadResponse(Resume resume) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resume.getFileName() + "\"");
        return ResponseEntity.ok()
                .headers(headers)
                .body(resume.getFileContent());
    }
}
