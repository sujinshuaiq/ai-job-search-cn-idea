package cn.jobsearch.mcp;

import cn.jobsearch.config.BrowserProperties;
import cn.jobsearch.config.CrawlerProperties;
import cn.jobsearch.service.browser.BrowserAutomationService;
import cn.jobsearch.service.crawler.BossHtmlParser;
import cn.jobsearch.service.crawler.BossHttpClient;
import cn.jobsearch.service.crawler.RobotsTxtService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class BossMcpServer {
    private BossMcpServer() {
    }

    public static void run() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        BrowserProperties browserProperties = browserProperties();
        CrawlerProperties crawlerProperties = new CrawlerProperties();
        BossHttpClient httpClient = new BossHttpClient(crawlerProperties);
        RobotsTxtService robotsTxtService = new RobotsTxtService(httpClient);
        BrowserAutomationService browser = new BrowserAutomationService(browserProperties, robotsTxtService,
                new BossHtmlParser());
        String apiBase = setting("JOB_SEARCH_API_BASE", "http://127.0.0.1:8080");
        BossMcpToolExecutor executor = new BossMcpToolExecutor(browser, objectMapper, apiBase);
        McpProtocolHandler protocol = new McpProtocolHandler(objectMapper, executor);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        System.err.println("JobPilot BOSS MCP STDIO 已就绪；协议 " + McpProtocolHandler.PROTOCOL_VERSION);
        try {
            String line;
            while (!protocol.isExitRequested() && (line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                ObjectNode response = protocol.handleLine(line);
                if (response != null) {
                    writer.println(objectMapper.writeValueAsString(response));
                }
            }
        } catch (Exception ex) {
            System.err.println("MCP STDIO 服务终止：" + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            browser.shutdown();
            System.err.println("JobPilot BOSS MCP STDIO 已退出");
        }
    }

    private static BrowserProperties browserProperties() {
        BrowserProperties properties = new BrowserProperties();
        properties.setEnabled(Boolean.parseBoolean(setting("BOSS_BROWSER_ENABLED", "false")));
        properties.setProfileDir(setting("BOSS_BROWSER_PROFILE", "./data/browser-profile"));
        properties.setHeadless(Boolean.parseBoolean(setting("BOSS_BROWSER_HEADLESS", "false")));
        properties.setChannel(setting("BOSS_BROWSER_CHANNEL", "chrome"));
        properties.setExecutablePath(setting("BOSS_BROWSER_EXECUTABLE_PATH", ""));
        properties.setNavigationTimeoutMs(parseInt(setting("BOSS_BROWSER_TIMEOUT_MS", "45000"), 45000));
        return properties;
    }

    private static String setting(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) return property.trim();
        String environment = System.getenv(name);
        return environment == null || environment.isBlank() ? fallback : environment.trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 1000 && parsed <= 180000 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
