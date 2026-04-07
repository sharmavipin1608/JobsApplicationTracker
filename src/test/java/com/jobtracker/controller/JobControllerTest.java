package com.jobtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.CreateJobRequest;
import com.jobtracker.dto.JobResponse;
import com.jobtracker.enums.JobStatus;
import com.jobtracker.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturn201_whenValidRequest() throws Exception {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, null, null
        );
        JobResponse response = new JobResponse(
                UUID.randomUUID(), "Acme Corp", "Senior Engineer",
                JobStatus.UNDETERMINED, null, LocalDateTime.now()
        );

        when(jobService.createJob(any(CreateJobRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Acme Corp"))
                .andExpect(jsonPath("$.role").value("Senior Engineer"))
                .andExpect(jsonPath("$.status").value("UNDETERMINED"));
    }

    @Test
    void shouldReturn400_whenCompanyIsMissing() throws Exception {
        String requestJson = """
                {
                    "role": "Senior Engineer"
                }
                """;

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_whenRoleIsMissing() throws Exception {
        String requestJson = """
                {
                    "company": "Acme Corp"
                }
                """;

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_whenStatusIsInvalid() throws Exception {
        String requestJson = """
                {
                    "company": "Acme Corp",
                    "role": "Senior Engineer",
                    "status": "INVALID_STATUS"
                }
                """;

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCallService_whenValidRequest() throws Exception {
        CreateJobRequest request = new CreateJobRequest(
                "Acme Corp", "Senior Engineer", null, JobStatus.APPLIED, null
        );
        JobResponse response = new JobResponse(
                UUID.randomUUID(), "Acme Corp", "Senior Engineer",
                JobStatus.APPLIED, null, LocalDateTime.now()
        );

        when(jobService.createJob(any(CreateJobRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(jobService).createJob(any(CreateJobRequest.class));
    }
}
