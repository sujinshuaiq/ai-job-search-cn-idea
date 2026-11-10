package cn.jobsearch;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-frontend-browser;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.demo.seed-enabled=false",
        "app.ai.api-key="
})
class AgentFrontendBrowserIT {
    private static final Path CHROME = Path.of("C:/Program Files/Google/Chrome/Application/chrome.exe");

    @LocalServerPort int port;

    @Test
    void rendersAgentAndCompletesLocalRunWithoutBrowserErrors() {
        assumeTrue(Files.isRegularFile(CHROME), "本机未安装标准路径 Chrome");
        List<String> errors = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
                     .setExecutablePath(CHROME).setHeadless(true))) {
            Page page = browser.newPage();
            page.onPageError(errors::add);
            page.navigate("http://127.0.0.1:" + port + "/");
            page.locator(".nav-item[data-view='agent']").click();
            page.waitForFunction("() => !document.getElementById('view-agent').hidden");
            page.waitForTimeout(500);

            assertThat(page.locator("#page-title").textContent()).isEqualTo("求职 Agent");
            assertThat(page.locator("#agent-goal").isVisible()).isTrue();
            page.locator("[data-agent-example]").nth(1).click();
            assertThat(page.locator("#agent-goal").inputValue()).contains("离线 HTML", "杭州");

            page.locator("#agent-goal").fill("查询职位库里的 Java 岗位");
            page.locator("#agent-start").click();
            page.waitForFunction("() => document.getElementById('agent-run-status').textContent === '已完成'");
            assertThat(page.locator("#agent-answer").textContent()).contains("未调用大模型或工具");
            assertThat(page.locator("#agent-steps .agent-step").count()).isEqualTo(2);
            assertThat(page.locator("#agent-confirmation").isVisible()).isFalse();
            page.screenshot(new Page.ScreenshotOptions().setPath(Path.of("target/agent-workbench.png")).setFullPage(true));

            page.locator(".nav-item[data-view='crawler']").click();
            assertThat(page.locator("#crawler-offline").isVisible()).isTrue();
            assertThat(page.locator("#crawler-browser").isVisible()).isFalse();
            assertThat(page.locator(".crawler-tab.active").getAttribute("data-crawler-tab")).isEqualTo("offline");
            page.locator(".nav-item[data-view='agent']").click();

            page.setViewportSize(390, 1000);
            page.waitForTimeout(200);
            Number scrollWidth = (Number) page.evaluate("document.documentElement.scrollWidth");
            assertThat(scrollWidth.intValue()).isLessThanOrEqualTo(390);
            page.screenshot(new Page.ScreenshotOptions().setPath(Path.of("target/agent-workbench-mobile.png")).setFullPage(true));
            assertThat(errors).isEmpty();
        }
    }

    @Test
    void pollsBackgroundRunWithoutKeepingTheSubmitRequestOpen() {
        assumeTrue(Files.isRegularFile(CHROME), "本机未安装标准路径 Chrome");
        java.util.concurrent.atomic.AtomicInteger polls = new java.util.concurrent.atomic.AtomicInteger();
        List<String> errors = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
                     .setExecutablePath(CHROME).setHeadless(true))) {
            Page page = browser.newPage();
            page.onPageError(errors::add);
            page.route("**/api/agent/runs", route -> route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                    .setStatus(200).setContentType("application/json").setBody("""
                            {"id":42,"conversationId":"browser-async","goal":"查询 Java 岗位","status":"RUNNING",
                             "mode":"DEEPSEEK","steps":[{"id":1,"toolName":"user_goal","summary":"查询 Java 岗位",
                             "status":"SUCCESS","durationMs":0,"createdAt":"2026-11-10 10:00:00"}],
                             "updatedAt":"2026-11-10 10:00:00"}
                            """)));
            page.route("**/api/agent/runs/42", route -> {
                polls.incrementAndGet();
                route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(200).setContentType("application/json").setBody("""
                                {"id":42,"conversationId":"browser-async","goal":"查询 Java 岗位","status":"COMPLETED",
                                 "mode":"DEEPSEEK","response":"后台模型回答已完成","steps":[{"id":1,"toolName":"user_goal",
                                 "summary":"查询 Java 岗位","status":"SUCCESS","durationMs":0,"createdAt":"2026-11-10 10:00:00"}],
                                 "updatedAt":"2026-11-10 10:00:02"}
                                """));
            });
            page.navigate("http://127.0.0.1:" + port + "/");
            page.locator(".nav-item[data-view='agent']").click();
            page.locator("#agent-goal").fill("查询 Java 岗位");
            page.locator("#agent-start").click();
            page.waitForFunction("() => document.getElementById('agent-run-status').textContent === '执行中'");
            page.waitForFunction("() => document.getElementById('agent-run-status').textContent === '已完成'");

            assertThat(page.locator("#agent-answer").textContent()).isEqualTo("后台模型回答已完成");
            assertThat(polls.get()).isGreaterThanOrEqualTo(1);
            assertThat(errors).isEmpty();
        }
    }
}
