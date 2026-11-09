package cn.jobsearch.service.agent;

import cn.jobsearch.config.AiProperties;
import cn.jobsearch.dto.FitAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void callsOpenAiCompatibleEndpointAndParsesJson() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"matchScore\\\":88,\\\"summary\\\":\\\"匹配良好\\\",\\\"strengths\\\":[\\\"Java\\\"],\\\"gaps\\\":[]}\\n```\"}}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        AiProperties properties = new AiProperties();
        properties.setApiKey("test-secret");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setModel("deepseek-test");
        DeepSeekClient client = new DeepSeekClient(properties, new ObjectMapper());
        FitAnalysisResult result = client.completeJson("system", "user", FitAnalysisResult.class);

        assertThat(result.getMatchScore()).isEqualTo(88);
        assertThat(result.getSummary()).isEqualTo("匹配良好");
        assertThat(authorization.get()).isEqualTo("Bearer test-secret");
        assertThat(requestBody.get()).contains("deepseek-test", "response_format", "json_object", "\"stream\":false");
    }
}
