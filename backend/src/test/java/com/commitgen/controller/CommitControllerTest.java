package com.commitgen.controller;

import com.commitgen.dto.CommitResponse;
import com.commitgen.dto.CommitResponse.Suggestion;
import com.commitgen.service.CommitService;
import com.commitgen.service.RateLimitService;
import com.commitgen.service.RateLimitService.RateLimitInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommitController.class)
class CommitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommitService commitService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final long RESET_EPOCH = Instant.now().plusSeconds(3600).getEpochSecond();

    @Test
    @DisplayName("POST /api/generate retorna 200 com sugestões e headers de rate limit")
    void shouldReturn200WithSuggestions() throws Exception {
        RateLimitInfo info = new RateLimitInfo(true, 9, 10, RESET_EPOCH, 0);
        when(rateLimitService.consume(anyString())).thenReturn(info);
        when(commitService.generateCommitMessages(any())).thenReturn(CommitResponse.builder()
                .suggestions(List.of(Suggestion.builder()
                        .message("feat: add feature")
                        .type("feat")
                        .description("add feature")
                        .build()))
                .model("llama-3.3-70b-versatile")
                .processingTimeMs(150L)
                .build());

        String body = """
                {
                    "diff": "+ new line added",
                    "language": "en",
                    "style": "conventional",
                    "quantity": 1
                }
                """;

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].message").value("feat: add feature"))
                .andExpect(jsonPath("$.model").value("llama-3.3-70b-versatile"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"))
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    @DisplayName("POST /api/generate retorna 429 quando rate limit excedido com headers")
    void shouldReturn429WhenRateLimited() throws Exception {
        RateLimitInfo info = new RateLimitInfo(false, 0, 10, RESET_EPOCH, 360);
        when(rateLimitService.consume(anyString())).thenReturn(info);

        String body = """
                {
                    "diff": "+ some change",
                    "language": "en",
                    "style": "conventional",
                    "quantity": 1
                }
                """;

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "360"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @ParameterizedTest(name = "POST /api/generate retorna 400 para body inválido: [{index}]")
    @ValueSource(strings = {
            "{\"diff\": \"\", \"language\": \"en\", \"style\": \"conventional\", \"quantity\": 1}",
            "{\"diff\": \"+ change\", \"language\": \"en\", \"style\": \"conventional\", \"quantity\": 10}",
            "{\"diff\": \"+ change\", \"language\": \"en\", \"style\": \"conventional\", \"quantity\": 0}"
    })
    void shouldReturn400ForInvalidRequest(String body) throws Exception {
        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/rate-limit retorna status do rate limit")
    void shouldReturnRateLimitInfo() throws Exception {
        RateLimitInfo info = new RateLimitInfo(true, 7, 10, RESET_EPOCH, 0);
        when(rateLimitService.getInfo(anyString())).thenReturn(info);

        mockMvc.perform(get("/api/rate-limit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(7))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.resetAt").isNumber())
                .andExpect(header().string("X-RateLimit-Remaining", "7"))
                .andExpect(header().string("X-RateLimit-Limit", "10"));
    }

    @Test
    @DisplayName("GET /api/health retorna status UP")
    void shouldReturnHealthUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("commit-message-generator"));
    }
}
