package cn.jobsearch.mcp;

import cn.jobsearch.service.browser.BrowserAutomationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class McpProtocolHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void initializesAndListsAllFiveTools() {
        BossMcpToolExecutor executor = mock(BossMcpToolExecutor.class);
        McpProtocolHandler handler = new McpProtocolHandler(objectMapper, executor);

        ObjectNode initialized = handler.handleLine("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertThat(initialized.at("/result/protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(initialized.at("/result/serverInfo/name").asText()).isEqualTo("jobpilot-boss-browser");

        ObjectNode listed = handler.handleLine("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
        List<String> names = new java.util.ArrayList<>();
        listed.at("/result/tools").forEach(node -> names.add(node.path("name").asText()));
        assertThat(names).containsExactlyInAnyOrder("boss_browser_start", "boss_open_jobs_page",
                "boss_browser_status", "boss_collect_current_page", "boss_browser_close");
        assertThat(handler.handleLine("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}").at("/result").isObject())
                .isTrue();
        verifyNoInteractions(executor);
    }

    @Test
    void returnsToolErrorWhenCurrentPageWasNotConfirmed() throws Exception {
        BrowserAutomationService browser = mock(BrowserAutomationService.class);
        BossMcpToolExecutor executor = new BossMcpToolExecutor(browser, objectMapper, "http://127.0.0.1:8080");
        McpProtocolHandler handler = new McpProtocolHandler(objectMapper, executor);
        String request = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"boss_collect_current_page\",\"arguments\":{\"userConfirmed\":false}}}";

        ObjectNode response = handler.handleLine(request);

        assertThat(response.at("/result/isError").asBoolean()).isTrue();
        JsonNode error = objectMapper.readTree(response.at("/result/content/0/text").asText());
        assertThat(error.path("error").asText()).isEqualTo("USER_CONFIRMATION_REQUIRED");
        verifyNoInteractions(browser);
    }

    @Test
    void returnsJsonRpcErrorForMalformedInputAndUnknownMethod() {
        McpProtocolHandler handler = new McpProtocolHandler(objectMapper, mock(BossMcpToolExecutor.class));

        assertThat(handler.handleLine("not-json").at("/error/code").asInt()).isEqualTo(-32700);
        assertThat(handler.handleLine("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"missing\"}")
                .at("/error/code").asInt()).isEqualTo(-32601);
    }
}
