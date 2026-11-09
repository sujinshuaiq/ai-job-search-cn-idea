package cn.jobsearch.mcp;

import cn.jobsearch.service.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

public class McpProtocolHandler {
    public static final String PROTOCOL_VERSION = "2024-11-05";
    private static final Set<String> TOOL_NAMES = Set.of(
            "boss_browser_start", "boss_open_jobs_page", "boss_browser_status",
            "boss_collect_current_page", "boss_browser_close");

    private final ObjectMapper objectMapper;
    private final BossMcpToolExecutor executor;
    private boolean exitRequested;

    public McpProtocolHandler(ObjectMapper objectMapper, BossMcpToolExecutor executor) {
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public ObjectNode handleLine(String line) {
        try {
            return handle(objectMapper.readTree(line));
        } catch (Exception ex) {
            return protocolError(NullNode.instance, -32700, "Parse error: 请求必须是单行合法 JSON");
        }
    }

    public ObjectNode handle(JsonNode request) {
        JsonNode id = request != null && request.has("id") ? request.get("id") : NullNode.instance;
        if (request == null || !request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual()) {
            return id.isNull() ? null : protocolError(id, -32600, "Invalid Request");
        }
        String method = request.get("method").asText();
        boolean notification = !request.has("id");
        try {
            return switch (method) {
                case "initialize" -> notification ? null : result(id, initializeResult());
                case "notifications/initialized", "notifications/cancelled" -> null;
                case "ping" -> notification ? null : result(id, objectMapper.createObjectNode());
                case "tools/list" -> notification ? null : result(id, toolsList());
                case "tools/call" -> notification ? null : result(id, callTool(request.path("params")));
                case "shutdown" -> notification ? null : result(id, objectMapper.createObjectNode());
                case "exit" -> {
                    exitRequested = true;
                    yield null;
                }
                default -> notification ? null : protocolError(id, -32601, "Method not found: " + method);
            };
        } catch (Exception ex) {
            return notification ? null : protocolError(id, -32603, "Internal error: " + safeMessage(ex));
        }
    }

    public boolean isExitRequested() {
        return exitRequested;
    }

    private ObjectNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "jobpilot-boss-browser");
        serverInfo.put("version", "1.0.0");
        result.put("instructions", "只打开无 query 的 BOSS 基础页；用户手动登录和搜索后，必须取得 userConfirmed=true 才能读取当前页。"
                + "不得自动翻页、导出 Cookie 或绕过验证。importToApp 默认 false。");
        return result;
    }

    private ObjectNode toolsList() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        addTool(tools, "boss_browser_start", "启动本地持久化 Chrome/Edge；首次建议使用有头模式并人工扫码。",
                "{\"type\":\"object\",\"properties\":{\"headless\":{\"type\":\"boolean\",\"description\":\"仅已建立会话时使用 true\"}},\"additionalProperties\":false}");
        addTool(tools, "boss_open_jobs_page", "robots.txt 检查后打开 BOSS 首页或无 query 的基础职位页；不自动搜索。",
                "{\"type\":\"object\",\"properties\":{\"jobsPage\":{\"type\":\"boolean\",\"default\":true},\"headless\":{\"type\":\"boolean\"}},\"additionalProperties\":false}");
        addTool(tools, "boss_browser_status", "读取当前标签页 URL、标题和人工操作状态，不读取 Cookie。",
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");
        addTool(tools, "boss_collect_current_page", "在用户手动搜索并确认后读取当前 BOSS 页职位卡片；单次最多 20 个，不自动翻页。",
                "{\"type\":\"object\",\"properties\":{\"userConfirmed\":{\"type\":\"boolean\",\"const\":true,\"description\":\"用户明确确认当前页面后必须为 true\"},\"city\":{\"type\":\"string\",\"maxLength\":80},\"maxItems\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20,\"default\":10},\"importToApp\":{\"type\":\"boolean\",\"default\":false}},\"required\":[\"userConfirmed\"],\"additionalProperties\":false}");
        addTool(tools, "boss_browser_close", "关闭浏览器和驱动，保留本地持久化会话目录。",
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");
        return result;
    }

    private ObjectNode callTool(JsonNode params) {
        String name = params.path("name").asText("");
        JsonNode arguments = params.path("arguments");
        if (!TOOL_NAMES.contains(name)) {
            return toolError("UNKNOWN_TOOL", "未知工具：" + name);
        }
        try {
            return toolResult(executor.execute(name, arguments), false);
        } catch (ApiException ex) {
            return toolError(ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            return toolError("MCP_TOOL_ERROR", safeMessage(ex));
        }
    }

    private ObjectNode toolResult(JsonNode payload, boolean isError) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        try {
            text.put("text", objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            text.put("text", "{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"工具结果序列化失败\"}");
            result.put("isError", true);
            return result;
        }
        result.put("isError", isError);
        return result;
    }

    private ObjectNode toolError(String code, String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("error", code == null || code.isBlank() ? "MCP_TOOL_ERROR" : code);
        error.put("message", message == null ? "工具执行失败" : message);
        return toolResult(error, true);
    }

    private void addTool(ArrayNode tools, String name, String description, String schema) {
        try {
            ObjectNode tool = tools.addObject();
            tool.put("name", name);
            tool.put("description", description);
            tool.set("inputSchema", objectMapper.readTree(schema));
        } catch (Exception ex) {
            throw new IllegalStateException("内置 MCP 工具 Schema 无效", ex);
        }
    }

    private ObjectNode result(JsonNode id, JsonNode payload) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", payload);
        return response;
    }

    private ObjectNode protocolError(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
