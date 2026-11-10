package cn.jobsearch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:browser-api-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.demo.seed-enabled=false",
        "app.ai.api-key="
})
@AutoConfigureMockMvc
class BrowserApiIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void parsesIntentAndReadsStoppedStatusWithoutLaunchingBrowser() throws Exception {
        mvc.perform(post("/api/browser/boss/intent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"帮我搜集杭州 Java 后端岗位数据，最多 10 个\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Java 后端"))
                .andExpect(jsonPath("$.city").value("杭州"))
                .andExpect(jsonPath("$.maxItems").value(10))
                .andExpect(jsonPath("$.mode").value("LOCAL_RULES"))
                .andExpect(jsonPath("$.requiresManualSearch").value(true));
        mvc.perform(get("/api/browser/boss/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.pageAvailable").value(false));
    }

    @Test
    void rejectsCollectionWithoutConfirmationOrAbovePageLimit() throws Exception {
        mvc.perform(post("/api/browser/boss/collect").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"杭州\",\"maxItems\":10,\"confirmedCurrentPage\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/browser/boss/collect").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"杭州\",\"maxItems\":21,\"confirmedCurrentPage\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void blocksOnlineAndBrowserOperationsBeforeAnyNetworkAccessByDefault() throws Exception {
        mvc.perform(post("/api/browser/boss/open").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobsPage\":true,\"headless\":false}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("BOSS_BROWSER_DISABLED"));
        mvc.perform(post("/api/browser/boss/collect").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"杭州\",\"maxItems\":10,\"confirmedCurrentPage\":true}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("BOSS_BROWSER_DISABLED"));
        mvc.perform(post("/api/crawler/boss/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Java\",\"cityCode\":\"101210100\",\"city\":\"杭州\",\"page\":1}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("BOSS_ONLINE_DISABLED"));
    }
}
