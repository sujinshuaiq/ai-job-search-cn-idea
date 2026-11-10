package cn.jobsearch.service.agent;

import cn.jobsearch.dto.ManualJobRequest;
import cn.jobsearch.dto.agent.AgentRunRequest;
import cn.jobsearch.dto.agent.AgentRunResponse;
import cn.jobsearch.service.JobService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spring-ai-tool-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.demo.seed-enabled=false",
        "app.ai.api-key=test-secret",
        "spring.ai.openai.api-key=test-secret",
        "app.ai.model=deepseek-test"
})
class SpringAiToolCallingIntegrationTest {
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final List<String> BODIES = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer SERVER = createServer();

    @Autowired JobAgentService agent;
    @Autowired JobService jobs;

    @DynamicPropertySource
    static void modelProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void resetServer() {
        REQUESTS.set(0);
        BODIES.clear();
        AUTHORIZATION.set(null);
    }

    @Test
    void executesReviewedToolAndSendsToolResultBackThroughSpringAi() throws InterruptedException {
        jobs.createManual(new ManualJobRequest("Java 后端工程师", "工具调用测试公司", "杭州", "西湖区",
                "20-28K", "3-5年", "本科", "", "Java、Spring Boot",
                "负责业务 API，要求 Java、Spring Boot、MySQL。"));

        AgentRunResponse response = agent.start(new AgentRunRequest("查询职位库里杭州的 Java 岗位", null));
        assertThat(response.status().name()).isEqualTo("RUNNING");
        for (int attempt = 0; attempt < 50 && response.status().name().equals("RUNNING"); attempt++) {
            Thread.sleep(100);
            response = agent.get(response.id());
        }

        assertThat(response.status().name()).as(response.response()).isEqualTo("COMPLETED");
        assertThat(response.response()).contains("已找到 1 个职位");
        assertThat(response.steps()).anyMatch(step -> step.toolName().equals("search_saved_jobs")
                && step.status().equals("SUCCESS") && step.summary().contains("1 个职位"));
        assertThat(REQUESTS.get()).isEqualTo(2);
        assertThat(AUTHORIZATION.get()).isEqualTo("Bearer test-secret");
        assertThat(BODIES.get(0)).contains("search_saved_jobs", "deepseek-test")
                .doesNotContain("open_boss_for_manual_search");
        assertThat(BODIES.get(1)).contains("\"role\":\"tool\"", "call_search_1");
    }

    private static HttpServer createServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
                BODIES.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                int request = REQUESTS.incrementAndGet();
                String body = request == 1 ? """
                        {"id":"chatcmpl-tool","object":"chat.completion","created":1,"model":"deepseek-test",
                         "choices":[{"index":0,"message":{"role":"assistant","content":null,
                         "tool_calls":[{"id":"call_search_1","type":"function","function":{"name":"search_saved_jobs",
                         "arguments":"{\\\"keyword\\\":\\\"Java\\\",\\\"city\\\":\\\"杭州\\\",\\\"maxItems\\\":3}"}}]},
                         "finish_reason":"tool_calls"}]}
                        """ : """
                        {"id":"chatcmpl-final","object":"chat.completion","created":2,"model":"deepseek-test",
                         "choices":[{"index":0,"message":{"role":"assistant","content":"已找到 1 个职位：Java 后端工程师，数据库 ID 可在执行轨迹中核对。"},
                         "finish_reason":"stop"}]}
                        """;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
