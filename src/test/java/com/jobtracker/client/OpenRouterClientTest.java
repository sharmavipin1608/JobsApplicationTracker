package com.jobtracker.client;

import com.jobtracker.config.OpenRouterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenRouterClientTest {

    private static final String BASE_URL = "http://test-openrouter";
    private static final String API_KEY = "test-key-123";
    private static final String MODEL = "mistralai/mistral-7b-instruct";
    private static final String REFERER = "http://localhost:8080";

    private MockRestServiceServer mockServer;
    private OpenRouterClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        OpenRouterProperties props = new OpenRouterProperties(API_KEY, BASE_URL, MODEL, REFERER);
        client = new OpenRouterClient(builder, props);
    }

    @Test
    void shouldSendCorrectRequestShape_andReturnAssistantContent() {
        mockServer.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(header("HTTP-Referer", REFERER))
                .andExpect(header("X-Title", "Job Application Tracker"))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("you are a parser"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("parse this jd"))
                .andExpect(jsonPath("$.temperature").value(0.3))
                .andExpect(jsonPath("$.max_tokens").value(500))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "skills: Java, Spring"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String result = client.complete("you are a parser", "parse this jd");

        assertThat(result).isEqualTo("skills: Java, Spring");
        mockServer.verify();
    }

    @Test
    void shouldThrowOpenRouterException_on4xxResponse() {
        mockServer.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"invalid api key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("sys", "user"))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("401");
    }

    @Test
    void shouldThrowOpenRouterException_on5xxResponse() {
        mockServer.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.complete("sys", "user"))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("500");
    }

    @Test
    void shouldThrowOpenRouterException_whenChoicesAreEmpty() {
        mockServer.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("sys", "user"))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("empty response");
    }
}
