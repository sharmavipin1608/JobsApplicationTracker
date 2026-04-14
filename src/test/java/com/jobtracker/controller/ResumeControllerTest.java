package com.jobtracker.controller;

import com.jobtracker.dto.ResumeResponse;
import com.jobtracker.exception.JobNotFoundException;
import com.jobtracker.exception.ResumeNotFoundException;
import com.jobtracker.model.Resume;
import com.jobtracker.service.ResumeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResumeController.class)
class ResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResumeService resumeService;

    private ResumeResponse sampleResponse(UUID jobId) {
        return new ResumeResponse(UUID.randomUUID(), jobId, "resume.txt", "Resume content", LocalDateTime.now());
    }

    private Resume buildResume(String fileName, byte[] content) {
        Resume resume = new Resume();
        resume.setFileName(fileName);
        resume.setFileContent(content);
        resume.setContentText("text");
        return resume;
    }

    @Test
    void shouldUploadMasterResume_andReturn201() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", "content".getBytes());
        when(resumeService.uploadMasterResume(any())).thenReturn(sampleResponse(null));

        mockMvc.perform(multipart("/api/v1/resume").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("resume.txt"));
    }

    @Test
    void shouldReturnCurrentMasterResume() throws Exception {
        when(resumeService.getCurrentMasterResume()).thenReturn(sampleResponse(null));

        mockMvc.perform(get("/api/v1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("resume.txt"));
    }

    @Test
    void shouldReturnNull_whenNoMasterResume() throws Exception {
        when(resumeService.getCurrentMasterResume()).thenReturn(null);

        mockMvc.perform(get("/api/v1/resume"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDownloadMasterResume() throws Exception {
        Resume resume = buildResume("resume.txt", "file content".getBytes());
        when(resumeService.downloadMasterResume()).thenReturn(resume);

        mockMvc.perform(get("/api/v1/resume/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"resume.txt\""))
                .andExpect(content().bytes("file content".getBytes()));
    }

    @Test
    void shouldReturn404_whenDownloadingNonexistentMasterResume() throws Exception {
        when(resumeService.downloadMasterResume()).thenThrow(new ResumeNotFoundException("No master resume found"));

        mockMvc.perform(get("/api/v1/resume/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUploadTailoredResume_andReturn201() throws Exception {
        UUID jobId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "tailored.txt", "text/plain", "content".getBytes());
        when(resumeService.uploadTailoredResume(eq(jobId), any())).thenReturn(sampleResponse(jobId));

        mockMvc.perform(multipart("/api/v1/jobs/{id}/resume", jobId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("resume.txt"));
    }

    @Test
    void shouldReturn404_whenUploadingTailoredResume_forNonexistentJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", "content".getBytes());
        when(resumeService.uploadTailoredResume(eq(jobId), any())).thenThrow(new JobNotFoundException(jobId));

        mockMvc.perform(multipart("/api/v1/jobs/{id}/resume", jobId).file(file))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnTailoredResume() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(resumeService.getTailoredResume(jobId)).thenReturn(sampleResponse(jobId));

        mockMvc.perform(get("/api/v1/jobs/{id}/resume", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("resume.txt"));
    }

    @Test
    void shouldDownloadTailoredResume() throws Exception {
        UUID jobId = UUID.randomUUID();
        Resume resume = buildResume("tailored.txt", "tailored content".getBytes());
        when(resumeService.downloadTailoredResume(jobId)).thenReturn(resume);

        mockMvc.perform(get("/api/v1/jobs/{id}/resume/download", jobId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"tailored.txt\""))
                .andExpect(content().bytes("tailored content".getBytes()));
    }

    @Test
    void shouldReturn400_forUnsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
        when(resumeService.uploadMasterResume(any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE"));

        mockMvc.perform(multipart("/api/v1/resume").file(file))
                .andExpect(status().isBadRequest());
    }
}
