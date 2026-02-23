package com.commitgen.service;

import com.commitgen.dto.CommitRequest;
import com.commitgen.dto.CommitResponse;
import com.commitgen.dto.CommitResponse.Suggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitServiceTest {

    @Mock
    private GroqApiService groqApiService;

    @InjectMocks
    private CommitService commitService;

    private CommitRequest request;

    @BeforeEach
    void setUp() {
        request = new CommitRequest();
        request.setDiff("+ added new feature\n- removed old code");
        request.setLanguage("en");
        request.setStyle("conventional");
        request.setQuantity(3);
    }

    @Nested
    @DisplayName("generateCommitMessages")
    class GenerateCommitMessages {

        @Test
        @DisplayName("deve gerar sugestões a partir de resposta convencional da IA")
        void shouldGenerateConventionalSuggestions() {
            String aiResponse = """
                    1. feat(core): add new feature
                    2. refactor(core): remove old code
                    3. chore: update dependencies
                    """;
            when(groqApiService.chat(anyString(), anyString())).thenReturn(aiResponse);
            when(groqApiService.getModel()).thenReturn("llama-3.3-70b-versatile");

            CommitResponse response = commitService.generateCommitMessages(request);

            assertThat(response.getSuggestions()).hasSize(3);
            assertThat(response.getModel()).isEqualTo("llama-3.3-70b-versatile");
            assertThat(response.getProcessingTimeMs()).isNotNull();

            Suggestion first = response.getSuggestions().get(0);
            assertThat(first.getType()).isEqualTo("feat");
            assertThat(first.getScope()).isEqualTo("core");
            assertThat(first.getDescription()).isEqualTo("add new feature");
        }

        @Test
        @DisplayName("deve lidar com resposta vazia da IA")
        void shouldHandleEmptyAiResponse() {
            when(groqApiService.chat(anyString(), anyString())).thenReturn("");
            when(groqApiService.getModel()).thenReturn("llama-3.3-70b-versatile");

            CommitResponse response = commitService.generateCommitMessages(request);

            assertThat(response.getSuggestions()).isEmpty();
        }

        @Test
        @DisplayName("deve lidar com resposta null da IA")
        void shouldHandleNullAiResponse() {
            when(groqApiService.chat(anyString(), anyString())).thenReturn(null);
            when(groqApiService.getModel()).thenReturn("llama-3.3-70b-versatile");

            CommitResponse response = commitService.generateCommitMessages(request);

            assertThat(response.getSuggestions()).isEmpty();
        }

        @Test
        @DisplayName("deve usar o model dinâmico do GroqApiService")
        void shouldUseDynamicModel() {
            when(groqApiService.chat(anyString(), anyString())).thenReturn("1. feat: test");
            when(groqApiService.getModel()).thenReturn("mixtral-8x7b-32768");

            CommitResponse response = commitService.generateCommitMessages(request);

            assertThat(response.getModel()).isEqualTo("mixtral-8x7b-32768");
        }
    }

    @Nested
    @DisplayName("parseResponse com estilos diferentes")
    class ParseResponseStyles {

        @Test
        @DisplayName("conventional: deve extrair type, scope e description")
        void shouldParseConventionalFormat() {
            String aiResponse = "1. fix(auth): resolve login timeout";
            when(groqApiService.chat(anyString(), anyString())).thenReturn(aiResponse);
            when(groqApiService.getModel()).thenReturn("test-model");

            request.setStyle("conventional");
            CommitResponse response = commitService.generateCommitMessages(request);

            Suggestion s = response.getSuggestions().get(0);
            assertThat(s.getType()).isEqualTo("fix");
            assertThat(s.getScope()).isEqualTo("auth");
            assertThat(s.getDescription()).isEqualTo("resolve login timeout");
        }

        @Test
        @DisplayName("conventional sem scope: scope deve ser null")
        void shouldParseConventionalWithoutScope() {
            String aiResponse = "1. docs: update README";
            when(groqApiService.chat(anyString(), anyString())).thenReturn(aiResponse);
            when(groqApiService.getModel()).thenReturn("test-model");

            request.setStyle("conventional");
            CommitResponse response = commitService.generateCommitMessages(request);

            Suggestion s = response.getSuggestions().get(0);
            assertThat(s.getType()).isEqualTo("docs");
            assertThat(s.getScope()).isNull();
            assertThat(s.getDescription()).isEqualTo("update README");
        }

        @Test
        @DisplayName("simple: deve retornar message sem type/scope")
        void shouldParseSimpleFormat() {
            String aiResponse = "1. Update the login page styling";
            when(groqApiService.chat(anyString(), anyString())).thenReturn(aiResponse);
            when(groqApiService.getModel()).thenReturn("test-model");

            request.setStyle("simple");
            CommitResponse response = commitService.generateCommitMessages(request);

            Suggestion s = response.getSuggestions().get(0);
            assertThat(s.getMessage()).isEqualTo("Update the login page styling");
            assertThat(s.getType()).isNull();
            assertThat(s.getScope()).isNull();
        }

        @Test
        @DisplayName("emoji: deve retornar mensagem inteira como description")
        void shouldParseEmojiFormat() {
            String aiResponse = "1. ✨ Add dark mode support";
            when(groqApiService.chat(anyString(), anyString())).thenReturn(aiResponse);
            when(groqApiService.getModel()).thenReturn("test-model");

            request.setStyle("emoji");
            CommitResponse response = commitService.generateCommitMessages(request);

            Suggestion s = response.getSuggestions().get(0);
            assertThat(s.getMessage()).contains("✨");
        }

        @Test
        @DisplayName("fallback: deve parsear linhas não numeradas quando formato não bate")
        void shouldFallbackToNonNumberedLines() {
            String aiResponse = "feat: add new endpoint\nfix: resolve bug";
            when(groqApiService.chat(anyString(), anyString())).thenReturn(aiResponse);
            when(groqApiService.getModel()).thenReturn("test-model");

            request.setStyle("conventional");
            CommitResponse response = commitService.generateCommitMessages(request);

            assertThat(response.getSuggestions()).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
