package cn.jobsearch.service.browser;

import cn.jobsearch.config.AiProperties;
import cn.jobsearch.dto.BrowserIntentRequest;
import cn.jobsearch.dto.BrowserIntentResponse;
import cn.jobsearch.service.agent.StructuredAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BossSearchIntentServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredAiService deepSeekClient = mock(StructuredAiService.class);

    @Test
    void parsesChineseIntentWithoutAiKey() {
        BossSearchIntentService service = new BossSearchIntentService(new AiProperties(), deepSeekClient, objectMapper);

        BrowserIntentResponse result = service.parse(new BrowserIntentRequest("帮我搜集杭州 Java 后端岗位数据，最多 8 个"));

        assertThat(result.query()).isEqualTo("Java 后端");
        assertThat(result.city()).isEqualTo("杭州");
        assertThat(result.maxItems()).isEqualTo(8);
        assertThat(result.mode()).isEqualTo("LOCAL_RULES");
        assertThat(result.requiresManualSearch()).isTrue();
    }

    @Test
    void removesStandaloneChineseSearchVerb() {
        BossSearchIntentService service = new BossSearchIntentService(new AiProperties(), deepSeekClient, objectMapper);

        BrowserIntentResponse result = service.parse(new BrowserIntentRequest("帮我在杭州找 10 个 Java 后端岗位"));

        assertThat(result.query()).isEqualTo("Java 后端");
        assertThat(result.city()).isEqualTo("杭州");
        assertThat(result.maxItems()).isEqualTo(10);
    }

    @Test
    void parsesEnglishCityKeywordAndLeadingCount() {
        BossSearchIntentService service = new BossSearchIntentService(new AiProperties(), deepSeekClient, objectMapper);

        BrowserIntentResponse result = service.parse(new BrowserIntentRequest("Find 5 Java backend jobs in Shanghai"));

        assertThat(result.query()).isEqualTo("Java backend");
        assertThat(result.city()).isEqualTo("上海");
        assertThat(result.maxItems()).isEqualTo(5);
        assertThat(result.mode()).isEqualTo("LOCAL_RULES");
    }

    @Test
    void usesDeepSeekJsonIntentWhenConfigured() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-key");
        when(deepSeekClient.completeJson(anyString(), anyString(), eq(BossSearchIntentService.AiIntent.class)))
                .thenReturn(new BossSearchIntentService.AiIntent("Spring Boot 开发", "深圳", 12));
        BossSearchIntentService service = new BossSearchIntentService(properties, deepSeekClient, objectMapper);

        BrowserIntentResponse result = service.parse(new BrowserIntentRequest("任意自然语言"));

        assertThat(result.query()).isEqualTo("Spring Boot 开发");
        assertThat(result.city()).isEqualTo("深圳");
        assertThat(result.maxItems()).isEqualTo(12);
        assertThat(result.mode()).isEqualTo("DEEPSEEK");
        assertThat(result.requiresManualSearch()).isTrue();
    }
}
