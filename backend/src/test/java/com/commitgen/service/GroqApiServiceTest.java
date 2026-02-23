package com.commitgen.service;

import com.commitgen.exception.GroqApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class GroqApiServiceTest {

    @Test
    @DisplayName("deve extrair content da resposta da API")
    void shouldExtractContentFromApiResponse() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.groq.com/openai/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String jsonResponse = """
                {
                    "choices": [{
                        "message": {
                            "content": "1. feat: add new endpoint"
                        }
                    }]
                }
                """;

        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GroqApiService service = new GroqApiService(builder.build());
        ReflectionTestUtils.setField(service, "model", "llama-3.3-70b-versatile");

        String result = service.chat("system prompt", "user prompt");

        assertThat(result).isEqualTo("1. feat: add new endpoint");
        server.verify();
    }

    @Test
    @DisplayName("deve lançar GroqApiException quando resposta é null")
    void shouldThrowOnNullResponse() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.groq.com/openai/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        GroqApiService service = new GroqApiService(builder.build());
        ReflectionTestUtils.setField(service, "model", "llama-3.3-70b-versatile");

        assertThatThrownBy(() -> service.chat("sys", "user"))
                .isInstanceOf(GroqApiException.class);
        server.verify();
    }

    @Test
    @DisplayName("deve lançar GroqApiException no erro HTTP")
    void shouldThrowOnHttpError() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.groq.com/openai/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withServerError());

        GroqApiService service = new GroqApiService(builder.build());
        ReflectionTestUtils.setField(service, "model", "llama-3.3-70b-versatile");

        assertThatThrownBy(() -> service.chat("sys", "user"))
                .isInstanceOf(GroqApiException.class)
                .hasMessageContaining("Failed to call GroqCloud API");
        server.verify();
    }

    @Test
    @DisplayName("deve lançar GroqApiException quando choices está vazio")
    void shouldThrowOnEmptyChoices() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.groq.com/openai/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String jsonResponse = """
                {
                    "choices": []
                }
                """;

        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GroqApiService service = new GroqApiService(builder.build());
        ReflectionTestUtils.setField(service, "model", "llama-3.3-70b-versatile");

        assertThatThrownBy(() -> service.chat("sys", "user"))
                .isInstanceOf(GroqApiException.class)
                .hasMessageContaining("No choices");
        server.verify();
    }
}
