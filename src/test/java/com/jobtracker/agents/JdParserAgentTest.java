package com.jobtracker.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.client.OpenRouterClient;
import com.jobtracker.exception.AgentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdParserAgentTest {

    @Mock
    private OpenRouterClient openRouterClient;

    private ObjectMapper objectMapper;
    private JdParserAgent agent;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        agent = new JdParserAgent(openRouterClient, objectMapper);
    }

    @Test
    void shouldParseCleanJsonResponse() {
        when(openRouterClient.complete(anyString(), anyString())).thenReturn("""
                {"skills": ["Java", "Spring Boot"], "seniority": "Senior", "domain": "Backend"}
                """);

        AgentInvocation<JdParseResult> invocation = agent.parse("some jd text");

        assertThat(invocation.result().skills()).containsExactly("Java", "Spring Boot");
        assertThat(invocation.result().seniority()).isEqualTo("Senior");
        assertThat(invocation.result().domain()).isEqualTo("Backend");
        assertThat(invocation.inputText()).isEqualTo("some jd text");
    }

    @Test
    void shouldParseMarkdownFencedJsonResponse() {
        when(openRouterClient.complete(anyString(), anyString())).thenReturn("""
                ```json
                {"skills": ["Python"], "seniority": "Mid", "domain": "Data"}
                ```
                """);

        AgentInvocation<JdParseResult> invocation = agent.parse("jd");

        assertThat(invocation.result().skills()).containsExactly("Python");
        assertThat(invocation.result().seniority()).isEqualTo("Mid");
    }

    @Test
    void shouldParseJsonWithSurroundingProse() {
        when(openRouterClient.complete(anyString(), anyString())).thenReturn("""
                Sure! Here is the parsed JD:
                {"skills": ["Go"], "seniority": "Staff", "domain": "Backend"}
                Let me know if you need anything else.
                """);

        AgentInvocation<JdParseResult> invocation = agent.parse("jd");

        assertThat(invocation.result().skills()).containsExactly("Go");
        assertThat(invocation.result().seniority()).isEqualTo("Staff");
    }

    @Test
    void shouldThrowAgentException_whenModelOutputIsNotJson() {
        when(openRouterClient.complete(anyString(), anyString()))
                .thenReturn("I cannot parse this job description, sorry.");

        assertThatThrownBy(() -> agent.parse("jd"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("Failed to parse model output");
    }

    @Test
    void shouldThrowAgentException_whenJdTextIsNullOrBlank() {
        assertThatThrownBy(() -> agent.parse(null))
                .isInstanceOf(AgentException.class);
        assertThatThrownBy(() -> agent.parse("   "))
                .isInstanceOf(AgentException.class);
    }

    @Test
    void shouldPassJdTextAsUserPrompt() {
        when(openRouterClient.complete(anyString(), anyString())).thenReturn("""
                {"skills": [], "seniority": "Junior", "domain": "Backend"}
                """);

        agent.parse("Build microservices in Java.");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(openRouterClient).complete(systemCaptor.capture(), userCaptor.capture());

        assertThat(systemCaptor.getValue()).contains("job description parser");
        assertThat(userCaptor.getValue()).isEqualTo("Build microservices in Java.");
    }
}
