package com.jobtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.service.OrchestratorService;
import com.jobtracker.dto.CreateJobRequest;
import com.jobtracker.dto.JobResponse;
import com.jobtracker.dto.ScoreResponse;
import com.jobtracker.dto.UpdateJobRequest;
import com.jobtracker.enums.JobStatus;
import com.jobtracker.exception.JobNotFoundException;
import com.jobtracker.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @MockBean
    private OrchestratorService orchestratorService;

    @Autowired
    private ObjectMapper objectMapper;

    private JobResponse sampleResponse(UUID id, JobStatus status) {
        return new JobResponse(id, "Acme Corp", "Senior Engineer", status, null, null, null, null, null, LocalDateTime.now(), null);
    }

    // ---------- POST ----------

    @Test
    void shouldReturn201_whenValidRequest() throws Exception {
        CreateJobRequest request = new CreateJobRequest("Acme Corp", "Senior Engineer", null, null, null, null, null);
        when(jobService.createJob(any(CreateJobRequest.class)))
                .thenReturn(sampleResponse(UUID.randomUUID(), JobStatus.UNDETERMINED));

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Acme Corp"))
                .andExpect(jsonPath("$.status").value("UNDETERMINED"));
    }

    @Test
    void shouldReturn400_whenCompanyIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"Senior Engineer\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_whenRoleIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme Corp\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_whenStatusIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme Corp\",\"role\":\"Engineer\",\"status\":\"INVALID_STATUS\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET list ----------

    @Test
    void shouldReturnAllJobs() throws Exception {
        when(jobService.findAll()).thenReturn(List.of(
                sampleResponse(UUID.randomUUID(), JobStatus.APPLIED),
                sampleResponse(UUID.randomUUID(), JobStatus.UNDETERMINED)
        ));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- GET by id ----------

    @Test
    void shouldReturnJob_whenIdExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.findById(id)).thenReturn(sampleResponse(id, JobStatus.APPLIED));

        mockMvc.perform(get("/api/v1/jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void shouldReturn404_whenJobNotFound_onGet() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.findById(id)).thenThrow(new JobNotFoundException(id));

        mockMvc.perform(get("/api/v1/jobs/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ---------- PATCH ----------

    @Test
    void shouldUpdateJob_whenValidPatch() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.updateJob(eq(id), any(UpdateJobRequest.class)))
                .thenReturn(sampleResponse(id, JobStatus.APPLIED));

        mockMvc.perform(patch("/api/v1/jobs/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\",\"notes\":\"Spoke to recruiter\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void shouldReturn404_whenJobNotFound_onPatch() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.updateJob(eq(id), any(UpdateJobRequest.class)))
                .thenThrow(new JobNotFoundException(id));

        mockMvc.perform(patch("/api/v1/jobs/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400_whenPatchStatusIsInvalid() throws Exception {
        mockMvc.perform(patch("/api/v1/jobs/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- DELETE ----------

    @Test
    void shouldReturn204_whenJobDeleted() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/jobs/" + id))
                .andExpect(status().isNoContent());

        verify(jobService).deleteJob(id);
    }

    @Test
    void shouldReturn404_whenJobNotFound_onDelete() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new JobNotFoundException(id)).when(jobService).deleteJob(id);

        mockMvc.perform(delete("/api/v1/jobs/" + id))
                .andExpect(status().isNotFound());
    }

    // ---------- POST analyze ----------

    @Test
    void shouldReturn202_whenAnalyzeTriggered() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJdText(id)).thenReturn("Some JD text about Java and Spring Boot");

        mockMvc.perform(post("/api/v1/jobs/" + id + "/analyze"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Analysis started"))
                .andExpect(jsonPath("$.jobId").value(id.toString()));

        verify(orchestratorService).analyze(eq(id), eq("Some JD text about Java and Spring Boot"));
    }

    @Test
    void shouldReturn400_whenJdTextIsNull_onAnalyze() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJdText(id)).thenReturn(null);

        mockMvc.perform(post("/api/v1/jobs/" + id + "/analyze"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_JD_TEXT"));
    }

    @Test
    void shouldReturn400_whenJdTextIsBlank_onAnalyze() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJdText(id)).thenReturn("   ");

        mockMvc.perform(post("/api/v1/jobs/" + id + "/analyze"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_JD_TEXT"));
    }

    @Test
    void shouldReturn404_whenJobNotFound_onAnalyze() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJdText(id)).thenThrow(new JobNotFoundException(id));

        mockMvc.perform(post("/api/v1/jobs/" + id + "/analyze"))
                .andExpect(status().isNotFound());
    }

    // ---------- jdUrl ----------

    @Test
    void shouldReturnJdUrl_inJobResponse() throws Exception {
        UUID id = UUID.randomUUID();
        JobResponse response = new JobResponse(id, "Acme Corp", "Senior Engineer",
                JobStatus.APPLIED, null, null, null, "https://example.com/job", null, LocalDateTime.now(), null);
        when(jobService.findById(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jdUrl").value("https://example.com/job"));
    }

    @Test
    void shouldAcceptJdUrl_inCreateRequest() throws Exception {
        UUID id = UUID.randomUUID();
        JobResponse response = new JobResponse(id, "Acme Corp", "Senior Engineer",
                JobStatus.UNDETERMINED, null, null, null, "https://example.com/job", null, LocalDateTime.now(), null);
        when(jobService.createJob(any(CreateJobRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme Corp\",\"role\":\"Senior Engineer\",\"jdUrl\":\"https://example.com/job\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jdUrl").value("https://example.com/job"));
    }

    // ---------- GET score ----------

    @Test
    void shouldReturnScore_whenScoreExists() throws Exception {
        UUID id = UUID.randomUUID();
        ScoreResponse score = new ScoreResponse(UUID.randomUUID(), id, 82, "[\"Add GCP\"]", null);
        when(jobService.findLatestScore(id)).thenReturn(score);

        mockMvc.perform(get("/api/v1/jobs/" + id + "/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitScore").value(82))
                .andExpect(jsonPath("$.recommendations[0]").value("Add GCP"));
    }

    @Test
    void shouldReturnNull_whenNoScoreExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.findLatestScore(id)).thenReturn(null);

        mockMvc.perform(get("/api/v1/jobs/" + id + "/score"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn404_whenJobNotFound_onGetScore() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.findLatestScore(id)).thenThrow(new JobNotFoundException(id));

        mockMvc.perform(get("/api/v1/jobs/" + id + "/score"))
                .andExpect(status().isNotFound());
    }
}
