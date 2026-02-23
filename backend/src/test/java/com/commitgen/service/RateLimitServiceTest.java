package com.commitgen.service;

import com.commitgen.service.RateLimitService.RateLimitInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    // ===== Testes legados (tryConsume / getAvailableTokens) =====

    @Test
    @DisplayName("deve permitir a primeira requisição")
    void shouldAllowFirstRequest() {
        assertThat(rateLimitService.tryConsume("192.168.1.1")).isTrue();
    }

    @Test
    @DisplayName("deve permitir até 10 requisições por IP")
    void shouldAllowUpToMaxRequests() {
        String ip = "10.0.0.1";
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimitService.tryConsume(ip))
                    .as("Request %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("deve bloquear a 11ª requisição do mesmo IP")
    void shouldBlockAfterMaxRequests() {
        String ip = "10.0.0.2";
        for (int i = 0; i < 10; i++) {
            rateLimitService.tryConsume(ip);
        }
        assertThat(rateLimitService.tryConsume(ip)).isFalse();
    }

    @Test
    @DisplayName("IPs diferentes devem ter limites independentes")
    void shouldHaveIndependentLimitsPerIp() {
        String ip1 = "10.0.0.3";
        String ip2 = "10.0.0.4";

        // Esgota o limite do IP1
        for (int i = 0; i < 10; i++) {
            rateLimitService.tryConsume(ip1);
        }

        // IP2 deve estar livre
        assertThat(rateLimitService.tryConsume(ip2)).isTrue();
        // IP1 está bloqueado
        assertThat(rateLimitService.tryConsume(ip1)).isFalse();
    }

    @Test
    @DisplayName("deve retornar tokens disponíveis corretamente")
    void shouldReturnAvailableTokensCorrectly() {
        String ip = "10.0.0.5";
        assertThat(rateLimitService.getAvailableTokens(ip)).isEqualTo(10);

        rateLimitService.tryConsume(ip);
        assertThat(rateLimitService.getAvailableTokens(ip)).isEqualTo(9);

        // Consome mais 4
        for (int i = 0; i < 4; i++) {
            rateLimitService.tryConsume(ip);
        }
        assertThat(rateLimitService.getAvailableTokens(ip)).isEqualTo(5);
    }

    @Test
    @DisplayName("novo IP deve ter 10 tokens disponíveis")
    void newIpShouldHaveFullTokens() {
        assertThat(rateLimitService.getAvailableTokens("new-ip")).isEqualTo(10);
    }

    // ===== Testes do método consume() =====

    @Nested
    @DisplayName("consume()")
    class ConsumeTests {

        @Test
        @DisplayName("deve permitir e retornar info detalhada")
        void shouldAllowAndReturnInfo() {
            RateLimitInfo info = rateLimitService.consume("200.0.0.1");

            assertThat(info.isAllowed()).isTrue();
            assertThat(info.getRemaining()).isEqualTo(9);
            assertThat(info.getLimit()).isEqualTo(10);
            assertThat(info.getResetAtEpochSeconds()).isGreaterThan(0);
            assertThat(info.getRetryAfterSeconds()).isZero();
        }

        @Test
        @DisplayName("deve bloquear e retornar retryAfter > 0")
        void shouldBlockAndReturnRetryInfo() {
            String ip = "200.0.0.2";
            for (int i = 0; i < 10; i++) {
                rateLimitService.consume(ip);
            }

            RateLimitInfo info = rateLimitService.consume(ip);

            assertThat(info.isAllowed()).isFalse();
            assertThat(info.getRemaining()).isZero();
            assertThat(info.getLimit()).isEqualTo(10);
            assertThat(info.getRetryAfterSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("remaining deve decrementar a cada chamada")
        void shouldDecrementRemaining() {
            String ip = "200.0.0.3";

            RateLimitInfo first = rateLimitService.consume(ip);
            assertThat(first.getRemaining()).isEqualTo(9);

            RateLimitInfo second = rateLimitService.consume(ip);
            assertThat(second.getRemaining()).isEqualTo(8);
        }
    }

    // ===== Testes do método getInfo() =====

    @Nested
    @DisplayName("getInfo()")
    class GetInfoTests {

        @Test
        @DisplayName("IP novo deve retornar todas as informações com limit cheio")
        void newIpShouldReturnFullInfo() {
            RateLimitInfo info = rateLimitService.getInfo("300.0.0.1");

            assertThat(info.isAllowed()).isTrue();
            assertThat(info.getRemaining()).isEqualTo(10);
            assertThat(info.getLimit()).isEqualTo(10);
            assertThat(info.getResetAtEpochSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("não deve consumir tokens")
        void shouldNotConsumeTokens() {
            String ip = "300.0.0.2";

            rateLimitService.getInfo(ip);
            rateLimitService.getInfo(ip);
            rateLimitService.getInfo(ip);

            assertThat(rateLimitService.getAvailableTokens(ip)).isEqualTo(10);
        }

        @Test
        @DisplayName("deve refletir tokens consumidos via consume()")
        void shouldReflectConsumedTokens() {
            String ip = "300.0.0.3";

            rateLimitService.consume(ip);
            rateLimitService.consume(ip);

            RateLimitInfo info = rateLimitService.getInfo(ip);
            assertThat(info.getRemaining()).isEqualTo(8);
        }

        @Test
        @DisplayName("IP bloqueado deve retornar allowed=false")
        void blockedIpShouldReturnNotAllowed() {
            String ip = "300.0.0.4";
            for (int i = 0; i < 10; i++) {
                rateLimitService.consume(ip);
            }

            RateLimitInfo info = rateLimitService.getInfo(ip);
            assertThat(info.isAllowed()).isFalse();
            assertThat(info.getRemaining()).isZero();
        }
    }
}
