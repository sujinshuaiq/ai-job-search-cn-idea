package cn.jobsearch.service.browser;

import cn.jobsearch.config.BrowserProperties;
import cn.jobsearch.dto.BrowserStatusResponse;
import cn.jobsearch.service.ApiException;
import cn.jobsearch.service.crawler.BossHtmlParser;
import cn.jobsearch.service.crawler.RobotsTxtService;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrowserAutomationServiceTest {
    @Test
    void acceptsOnlyRealBossHttpsOrigin() {
        assertThat(BrowserAutomationService.requireBossUrl("https://www.zhipin.com/web/geek/job?query=Java").getHost())
                .isEqualTo("www.zhipin.com");
        assertThatThrownBy(() -> BrowserAutomationService.requireBossUrl("https://example.com/web/geek/job"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("UNEXPECTED_BROWSER_ORIGIN"));
        assertThatThrownBy(() -> BrowserAutomationService.requireBossUrl("https://www.zhipin.com.evil.example/jobs"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("UNEXPECTED_BROWSER_ORIGIN"));
        assertThatThrownBy(() -> BrowserAutomationService.requireBossUrl("http://www.zhipin.com/web/geek/job"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("UNEXPECTED_BROWSER_ORIGIN"));
    }

    @Test
    void serviceItselfRequiresUserConfirmationBeforeReadingBrowser() {
        BrowserAutomationService service = new BrowserAutomationService(new BrowserProperties(),
                mock(RobotsTxtService.class), new BossHtmlParser());
        try {
            assertThatThrownBy(() -> service.readCurrentJobs("杭州", 10, false))
                    .isInstanceOfSatisfying(ApiException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo("USER_CONFIRMATION_REQUIRED"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void disablesBrowserBeforeLaunchOrNetworkAccessByDefault() {
        RobotsTxtService robots = mock(RobotsTxtService.class);
        BrowserAutomationService service = new BrowserAutomationService(new BrowserProperties(), robots,
                new BossHtmlParser());
        try {
            assertThat(service.status().running()).isFalse();
            assertThat(service.status().message()).contains("默认离线安全模式");
            assertThatThrownBy(() -> service.start(false))
                    .isInstanceOfSatisfying(ApiException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo("BOSS_BROWSER_DISABLED"));
            verifyNoInteractions(robots);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void followsVisibleSearchTabWithoutNavigatingAgain() {
        RobotsTxtService robots = mock(RobotsTxtService.class);
        BrowserProperties properties = new BrowserProperties();
        properties.setEnabled(true);
        BrowserAutomationService service = new BrowserAutomationService(properties, robots,
                new BossHtmlParser());
        BrowserContext context = mock(BrowserContext.class);
        Page basePage = mock(Page.class);
        Page searchPage = mock(Page.class);
        stubPage(basePage, "https://www.zhipin.com/web/geek/job", "hidden");
        stubPage(searchPage, "https://www.zhipin.com/web/geek/job?query=Java", "visible");
        when(context.pages()).thenReturn(List.of(basePage, searchPage));
        ReflectionTestUtils.setField(service, "context", context);
        ReflectionTestUtils.setField(service, "currentPage", basePage);
        try {
            BrowserStatusResponse opened = service.openBoss(true, false);
            BrowserStatusResponse current = service.status();

            assertThat(opened.currentUrl()).isEqualTo("https://www.zhipin.com/web/geek/job?query=Java");
            assertThat(opened.message()).contains("没有重新导航");
            assertThat(current.currentUrl()).isEqualTo(opened.currentUrl());
            verify(basePage, never()).navigate(anyString(), org.mockito.ArgumentMatchers.any(Page.NavigateOptions.class));
            verify(searchPage, never()).navigate(anyString(), org.mockito.ArgumentMatchers.any(Page.NavigateOptions.class));
            verifyNoInteractions(robots);
        } finally {
            service.shutdown();
        }
    }

    private void stubPage(Page page, String url, String visibility) {
        when(page.isClosed()).thenReturn(false);
        when(page.url()).thenReturn(url);
        when(page.title()).thenReturn("BOSS直聘");
        when(page.evaluate(anyString())).thenAnswer(invocation -> {
            String script = invocation.getArgument(0);
            return script.contains("visibilityState") ? visibility : "";
        });
    }
}
