package cn.jobsearch.service.agent;

import cn.jobsearch.config.AiProperties;
import cn.jobsearch.dto.FitAnalysisResult;
import cn.jobsearch.service.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StructuredAiServiceTest {

    @Test
    void usesSpringAiStructuredOutput() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        FitAnalysisResult expected = new FitAnalysisResult();
        expected.setMatchScore(88);
        expected.setSummary("匹配良好");
        when(chatClient.prompt().system("system").user("user").call().entity(FitAnalysisResult.class))
                .thenReturn(expected);
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-secret");

        FitAnalysisResult result = new StructuredAiService(chatClient, properties)
                .completeJson("system", "user", FitAnalysisResult.class);

        assertThat(result.getMatchScore()).isEqualTo(88);
        assertThat(result.getSummary()).isEqualTo("匹配良好");
        verify(chatClient.prompt().system("system").user("user").call()).entity(FitAnalysisResult.class);
    }

    @Test
    void missingKeyNeverCallsTheModel() {
        ChatClient chatClient = mock(ChatClient.class);
        AiProperties properties = new AiProperties();
        properties.setApiKey("");

        assertThatThrownBy(() -> new StructuredAiService(chatClient, properties)
                .completeJson("system", "user", FitAnalysisResult.class))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("AI_NOT_CONFIGURED"));
        verifyNoInteractions(chatClient);
    }

    @Test
    void mapsAuthenticationFailureWithoutExposingTheKey() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system("system").user("user").call().entity(FitAnalysisResult.class))
                .thenThrow(new RuntimeException("HTTP 401 unauthorized"));
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-secret");

        assertThatThrownBy(() -> new StructuredAiService(chatClient, properties)
                .completeJson("system", "user", FitAnalysisResult.class))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("AI_AUTHENTICATION_FAILED");
                    assertThat(ex.getMessage()).doesNotContain("test-secret");
                });
    }
}
