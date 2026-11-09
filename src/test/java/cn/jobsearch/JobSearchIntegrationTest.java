package cn.jobsearch;

import cn.jobsearch.dto.ManualJobRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jobsearch-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.demo.seed-enabled=false",
        "app.ai.api-key="
})
@AutoConfigureMockMvc
class JobSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void completeLocalJobSearchFlow() throws Exception {
        ManualJobRequest request = new ManualJobRequest("Java 后端工程师", "端到端测试公司", "杭州", "西湖区",
                "20-28K", "3-5年", "本科", "", "Java、Spring Boot、MySQL",
                "负责真实业务 API，要求 Java、Spring Boot、MySQL。");
        String created = mvc.perform(post("/api/jobs/manual").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("MANUAL"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long jobId = objectMapper.readTree(created).get("id").asLong();

        mvc.perform(post("/api/agent/jobs/{id}/analyze", jobId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.matchScore").isNumber())
                .andExpect(jsonPath("$.strengths").isArray());
        mvc.perform(post("/api/agent/jobs/{id}/materials", jobId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.tailoredResume").isNotEmpty())
                .andExpect(jsonPath("$.interviewQuestions").isArray());

        String application = mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jobId", jobId, "status", "SAVED", "note", "准备投递"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long applicationId = objectMapper.readTree(application).get("id").asLong();
        mvc.perform(patch("/api/applications/{id}", applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPLIED", "note", "已人工投递"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));

        String html = new ClassPathResource("fixtures/boss-search.html").getContentAsString(StandardCharsets.UTF_8);
        String importBody = objectMapper.writeValueAsString(Map.of("city", "杭州", "html", html));
        String imported = mvc.perform(post("/api/crawler/boss/import").contentType(MediaType.APPLICATION_JSON).content(importBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parsed").value(2))
                .andExpect(jsonPath("$.imported").value(2)).andExpect(jsonPath("$.duplicates").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode importResult = objectMapper.readTree(imported);
        assertThat(importResult.get("jobIds")).hasSize(2);

        mvc.perform(post("/api/crawler/boss/import").contentType(MediaType.APPLICATION_JSON).content(importBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.duplicates").value(2));

        mvc.perform(get("/api/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(3))
                .andExpect(jsonPath("$.analyzedJobs").value(1))
                .andExpect(jsonPath("$.applications").value(1));
        mvc.perform(get("/api/jobs/{id}", jobId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisMode").value("LOCAL_DEMO"))
                .andExpect(jsonPath("$.greeting").isNotEmpty());
    }

    @Test
    void frontendAndFixtureAreServed() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("职航")));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/demo/boss-search.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("job-card-wrapper")));
        mvc.perform(get("/api/ai/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.mode").value("LOCAL_DEMO"));
        mvc.perform(get("/api/profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("请填写姓名"));
    }
}
