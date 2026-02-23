package com.commitgen.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("ErrorResponse.of deve criar resposta com todos os campos")
    void shouldCreateErrorResponseCorrectly() {
        ErrorResponse response = ErrorResponse.of(404, "Not Found");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getMessage()).isEqualTo("Not Found");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("GroqApiException deve preservar mensagem e causa")
    void shouldPreserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("connection refused");
        GroqApiException ex = new GroqApiException("API failed", cause);

        assertThat(ex.getMessage()).isEqualTo("API failed");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("GroqApiException deve funcionar só com mensagem")
    void shouldWorkWithMessageOnly() {
        GroqApiException ex = new GroqApiException("timeout");

        assertThat(ex.getMessage()).isEqualTo("timeout");
        assertThat(ex.getCause()).isNull();
    }
}
