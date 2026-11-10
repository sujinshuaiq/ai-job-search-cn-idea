package cn.jobsearch.mcp;

import cn.jobsearch.config.BrowserProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class BossMcpServerTest {
    @AfterEach
    void clearOverride() {
        System.clearProperty("BOSS_BROWSER_ENABLED");
    }

    @Test
    void keepsBrowserDisabledWhenOverrideIsFalse() {
        System.setProperty("BOSS_BROWSER_ENABLED", "false");
        BrowserProperties properties = ReflectionTestUtils.invokeMethod(BossMcpServer.class, "browserProperties");
        assertThat(properties).isNotNull();
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void readsExplicitBrowserEnablementForStandaloneMcp() {
        System.setProperty("BOSS_BROWSER_ENABLED", "true");
        BrowserProperties properties = ReflectionTestUtils.invokeMethod(BossMcpServer.class, "browserProperties");
        assertThat(properties).isNotNull();
        assertThat(properties.isEnabled()).isTrue();
    }
}
