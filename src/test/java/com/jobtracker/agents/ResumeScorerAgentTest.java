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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeScorerAgentTest {

    @Mock
    private OpenRouterClient openRouterClient;

    private ObjectMapper objectMapper;
    private ResumeScorerAgent agent;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        agent = new ResumeScorerAgent(openRouterClient, objectMapper);
    }

    private JdParseResult sampleJd() {
        return new JdParseResult(List.of("Java", "Spring Boot"), "Senior", "Backend");
    }

    @Test
    void shouldParseScoreResponse() {
        when(openRouterClient.complete(anyString(), anyString())).thenReturn("""
                {"fit_score": 82, "recommendations": ["Add GCP", "Highlight Kafka", "Mention payments"]}
                """);

        AgentInvocation<ScoreResult> invocation = agent.score(sampleJd());

        assertThat(invocation.result().fitScore()).isEqualTo(82);
        assertThat(invocation.result().recommendations()).hasSize(3);
        assertThat(invocation.result().recommendations()).contains("Add GCP");
    }

    @Test
    void shouldParseMarkdownFencedScoreResponse() {
        when(openRouterClient.complete(anyString(), anyString())).thenReturn("""
                ```json
                {"fit_score": 50, "recommendations": ["a", "b", "c"]}
                ```
                """);

        AgentInvocation<ScoreResult> invocation = agent.score(sampleJd());

        assertThat(invocation.result().fitScore()).isEqualTo(50);
    }

    @Test
    void shouldThrowAgentException_whenFitScoreOutOfRange() {
        when(openRouterClient.complete(anyString(), anyString())).thenReturn("""
                {"fit_score": 150, "recommendations": ["a", "b", "c"]}
                """);

        assertThatThrownBy(() -> agent.score(sampleJd()))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("out-of-range");
    }

    @Test
    void shouldThrowAgentException_whenModelOutputIsGarbage() {
        when(openRouterClient.complete(anyString(), anyString()))
                .thenReturn("I'm sorry I can't help with that");

        assertThatThrownBy(() -> agent.score(sampleJd()))
                .isInstanceOf(AgentException.class);
    }

    @Test
    void shouldThrowAgentException_whenParsedJdIsNull() {
        assertThatThrownBy(() -> agent.score(null))
                .isInstanceOf(AgentException.class);
    }

    @Test
    void shouldIncludeBothJdAndResumeInPrompt() {
        when(openRouterClient.complete(anyString(), anyString())).thenReturn("""
                {"fit_score": 75, "recommendations": ["a", "b", "c"]}
                """);

        agent.score(sampleJd());

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(openRouterClient).complete(anyString(), userCaptor.capture());

        String prompt = userCaptor.getValue();
        assertThat(prompt).contains("Parsed JD:");
        assertThat(prompt).contains("Java");
        assertThat(prompt).contains("Senior");
        assertThat(prompt).contains("Candidate resume:");
    }
}
