package cn.jobsearch.mcp;

import cn.jobsearch.service.ApiException;
import cn.jobsearch.service.browser.BrowserAutomationService;
import cn.jobsearch.service.browser.CurrentPageJobs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class BossMcpToolExecutor {
    private final BrowserAutomationService browser;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI importEndpoint;

    public BossMcpToolExecutor(BrowserAutomationService browser, ObjectMapper objectMapper, String apiBase) {
        this(browser, objectMapper, apiBase, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    BossMcpToolExecutor(BrowserAutomationService browser, ObjectMapper objectMapper, String apiBase,
                        HttpClient httpClient) {
        this.browser = browser;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        String base = apiBase == null || apiBase.isBlank() ? "http://127.0.0.1:8080" : apiBase.trim();
        this.importEndpoint = URI.create(base.replaceAll("/+$", "") + "/api/crawler/boss/import");
    }

    public ObjectNode execute(String toolName, JsonNode arguments) {
        JsonNode args = arguments == null || arguments.isNull() ? objectMapper.createObjectNode() : arguments;
        if (!args.isObject()) throw ApiException.badRequest("INVALID_TOOL_ARGUMENTS", "工具参数必须是 JSON 对象");
        return switch (toolName) {
            case "boss_browser_start" -> browserResult("浏览器已启动", browser.start(optionalBoolean(args, "headless")));
            case "boss_open_jobs_page" -> browserResult("基础页打开流程已结束",
                    browser.openBoss(!args.has("jobsPage") || args.path("jobsPage").asBoolean(true),
                            optionalBoolean(args, "headless")));
            case "boss_browser_status" -> browserResult("当前浏览器状态", browser.status());
            case "boss_collect_current_page" -> collect(args);
            case "boss_browser_close" -> browserResult("浏览器已关闭", browser.closeBrowser());
            default -> throw ApiException.badRequest("UNKNOWN_TOOL", "未知工具：" + toolName);
        };
    }

    private ObjectNode collect(JsonNode args) {
        if (!args.path("userConfirmed").asBoolean(false)) {
            throw ApiException.badRequest("USER_CONFIRMATION_REQUIRED",
                    "请先让用户在浏览器中手动搜索，并明确传入 userConfirmed=true");
        }
        int maxItems = args.has("maxItems") ? args.path("maxItems").asInt(0) : 10;
        String city = args.path("city").asText("").trim();
        if (city.length() > 80) throw ApiException.badRequest("INVALID_CITY", "城市名称不能超过 80 个字符");
        CurrentPageJobs current = browser.readCurrentJobs(city, maxItems, true);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("ok", true);
        result.put("collectionMode", "BOSS_BROWSER_CURRENT_PAGE");
        result.set("browser", objectMapper.valueToTree(current.browser()));
        result.put("parsed", current.jobs().size());
        result.set("jobs", objectMapper.valueToTree(current.jobs()));
        result.set("warnings", objectMapper.valueToTree(current.warnings()));
        boolean importToApp = args.path("importToApp").asBoolean(false);
        result.put("importToApp", importToApp);
        if (importToApp) {
            result.set("importResult", importCurrentPage(city, current.cardHtml()));
            result.put("message", "当前页职位已发送到本机 Web 应用去重入库");
        } else {
            result.put("message", "仅解析并返回当前页职位，未写入 Web 应用");
        }
        return result;
    }

    private JsonNode importCurrentPage(String city, String html) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("city", city, "html", html));
            HttpRequest request = HttpRequest.newBuilder(importEndpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = response.body().isBlank() ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = body.path("message").asText("Web 应用返回 HTTP " + response.statusCode());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "APP_IMPORT_FAILED",
                        "导入 Web 应用失败：" + message + "。请确认应用已启动且地址为 " + importEndpoint);
            }
            return body;
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "APP_IMPORT_INTERRUPTED", "导入 Web 应用被中断");
        } catch (IOException | IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "APP_IMPORT_FAILED",
                    "无法连接或解析本机 Web 应用响应；请先启动应用并检查 JOB_SEARCH_API_BASE");
        }
    }

    private ObjectNode browserResult(String message, Object browserStatus) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("ok", true);
        result.put("message", message);
        result.set("browser", objectMapper.valueToTree(browserStatus));
        return result;
    }

    private Boolean optionalBoolean(JsonNode args, String field) {
        return args.has(field) && !args.path(field).isNull() ? args.path(field).asBoolean() : null;
    }
}
