package cn.jobsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-run-api-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.demo.seed-enabled=false",
        "app.ai.api-key="
})
@AutoConfigureMockMvc
class AgentRunApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void localModeCreatesAnAuditableRunWithoutCallingModelOrTools() throws Exception {
        String body = mvc.perform(post("/api/agent/runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"查询职位库里的 Java 岗位\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.mode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.response").value(containsString("未调用大模型或工具")))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode run = objectMapper.readTree(body);

        mvc.perform(get("/api/agent/runs/{id}", run.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(run.get("conversationId").asText()))
                .andExpect(jsonPath("$.steps[0].toolName").value("user_goal"))
                .andExpect(jsonPath("$.steps[1].toolName").value("local_fallback"));
    }

    @Test
    void agentFrontendAndValidationAreAvailable() throws Exception {
        mvc.perform(get("/js/agent.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("startAgentRun")));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"view-agent\"")))
                .andExpect(content().string(containsString("/js/agent.js")));
        mvc.perform(post("/api/agent/runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
