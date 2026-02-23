package com.commitgen.service;

import com.commitgen.exception.GroqApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GroqApiService {

    private static final String CONTENT_KEY = "content";

    private final RestClient restClient;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    public GroqApiService(RestClient restClient) {
        this.restClient = restClient;
    }

    public String getModel() {
        return model;
    }

    @SuppressWarnings("unchecked")
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", CONTENT_KEY, systemPrompt),
                        Map.of("role", "user", CONTENT_KEY, userPrompt)
                ),
                "temperature", 0.7,
                "max_tokens", 500
        );

        try {
            log.debug("Calling GroqCloud API with model={}", model);
            Map<?, ?> response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new GroqApiException("Empty response from GroqCloud API");
            }

            List<Map<?, ?>> choices = (List<Map<?, ?>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new GroqApiException("No choices in GroqCloud response");
            }

            Map<?, ?> message = (Map<?, ?>) choices.get(0).get("message");
            return (String) message.get(CONTENT_KEY);

        } catch (RestClientException e) {
            log.error("Error calling GroqCloud API: {}", e.getMessage());
            throw new GroqApiException("Failed to call GroqCloud API: " + e.getMessage(), e);
        }
    }
}
