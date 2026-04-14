package com.jobtracker.service;

import com.jobtracker.dto.ResumeResponse;
import com.jobtracker.exception.JobNotFoundException;
import com.jobtracker.exception.ResumeNotFoundException;
import com.jobtracker.model.Job;
import com.jobtracker.model.Resume;
import com.jobtracker.repository.JobRepository;
import com.jobtracker.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private JobRepository jobRepository;

    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(resumeRepository, jobRepository);
    }

    private Resume buildSavedResume(UUID jobId, String fileName, String contentText) {
        Resume resume = new Resume();
        resume.setJobId(jobId);
        resume.setFileName(fileName);
        resume.setFileContent(contentText.getBytes());
        resume.setContentText(contentText);
        return resume;
    }

    @Test
    void shouldUploadMasterResume_withTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "My resume content".getBytes()
        );
        Resume saved = buildSavedResume(null, "resume.txt", "My resume content");
        when(resumeRepository.save(any(Resume.class))).thenReturn(saved);

        ResumeResponse response = resumeService.uploadMasterResume(file);

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Resume captured = captor.getValue();
        assertThat(captured.getJobId()).isNull();
        assertThat(captured.getFileName()).isEqualTo("resume.txt");
        assertThat(captured.getContentText()).isEqualTo("My resume content");
        assertThat(response).isNotNull();
    }

    @Test
    void shouldUploadTailoredResume_forExistingJob() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(new Job()));
        MockMultipartFile file = new MockMultipartFile(
                "file", "tailored.txt", "text/plain", "Tailored content".getBytes()
        );
        Resume saved = buildSavedResume(jobId, "tailored.txt", "Tailored content");
        when(resumeRepository.save(any(Resume.class))).thenReturn(saved);

        ResumeResponse response = resumeService.uploadTailoredResume(jobId, file);

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        assertThat(captor.getValue().getJobId()).isEqualTo(jobId);
        assertThat(response).isNotNull();
    }

    @Test
    void shouldThrowNotFound_whenUploadingTailoredResume_forNonexistentJob() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "content".getBytes()
        );

        assertThatThrownBy(() -> resumeService.uploadTailoredResume(jobId, file))
                .isInstanceOf(JobNotFoundException.class);
        verify(resumeRepository, never()).save(any());
    }

    @Test
    void shouldReject_unsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> resumeService.uploadMasterResume(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void shouldReject_emptyExtractedText() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", "   ".getBytes()
        );

        assertThatThrownBy(() -> resumeService.uploadMasterResume(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("EMPTY_RESUME_TEXT");
    }

    @Test
    void shouldReturnCurrentMasterResume() {
        Resume resume = buildSavedResume(null, "resume.txt", "Content");
        when(resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc()).thenReturn(Optional.of(resume));

        ResumeResponse response = resumeService.getCurrentMasterResume();

        assertThat(response).isNotNull();
        assertThat(response.fileName()).isEqualTo("resume.txt");
    }

    @Test
    void shouldReturnNull_whenNoMasterExists() {
        when(resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThat(resumeService.getCurrentMasterResume()).isNull();
    }

    @Test
    void shouldReturnTailoredResume_forJob() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(new Job()));
        Resume resume = buildSavedResume(jobId, "tailored.txt", "Tailored");
        when(resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId)).thenReturn(Optional.of(resume));

        ResumeResponse response = resumeService.getTailoredResume(jobId);

        assertThat(response).isNotNull();
        assertThat(response.fileName()).isEqualTo("tailored.txt");
    }

    @Test
    void shouldReturnResumeText_tailoredFirst_thenMaster_thenFallback() {
        UUID jobId = UUID.randomUUID();
        Resume tailored = buildSavedResume(jobId, "tailored.txt", "Tailored text");
        when(resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId)).thenReturn(Optional.of(tailored));

        String result = resumeService.getResumeTextForScoring(jobId);

        assertThat(result).isEqualTo("Tailored text");
        verify(resumeRepository, never()).findFirstByJobIdIsNullOrderByCreatedAtDesc();
    }

    @Test
    void shouldReturnMasterText_whenNoTailoredExists() {
        UUID jobId = UUID.randomUUID();
        when(resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId)).thenReturn(Optional.empty());
        Resume master = buildSavedResume(null, "master.txt", "Master text");
        when(resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc()).thenReturn(Optional.of(master));

        String result = resumeService.getResumeTextForScoring(jobId);

        assertThat(result).isEqualTo("Master text");
    }

    @Test
    void shouldReturnFallback_whenNoResumeExists() {
        UUID jobId = UUID.randomUUID();
        when(resumeRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId)).thenReturn(Optional.empty());
        when(resumeRepository.findFirstByJobIdIsNullOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        String result = resumeService.getResumeTextForScoring(jobId);

        assertThat(result).isEqualTo(ResumeService.RESUME_FALLBACK);
    }
}
