package cn.jobsearch.service.crawler;

import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.service.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BossHtmlParserTest {
    private final BossHtmlParser parser = new BossHtmlParser();

    @Test
    void parsesRealisticCardsAndDropsUnsafeContent() throws IOException {
        String html = new ClassPathResource("fixtures/boss-search.html").getContentAsString(StandardCharsets.UTF_8);
        ParseOutcome outcome = parser.parse(html, "杭州");

        assertThat(outcome.jobs()).hasSize(2);
        assertThat(outcome.warnings()).anyMatch(message -> message.contains("1 个卡片"));
        JobPosting first = outcome.jobs().get(0);
        assertThat(first.getTitle()).isEqualTo("AI 应用开发工程师");
        assertThat(first.getCompany()).isEqualTo("云谷测试科技");
        assertThat(first.getSalary()).isEqualTo("22-30K");
        assertThat(first.getExperienceRequirement()).isEqualTo("3-5年");
        assertThat(first.getEducationRequirement()).isEqualTo("本科");
        assertThat(first.getSourceUrl()).isEqualTo("https://www.zhipin.com/job_detail/test-ai-java-001.html");
        assertThat(first.getTags()).contains("Java", "RAG");
        assertThat(first.getDescription()).doesNotContain("ignore instructions", "leak secrets");

        JobPosting second = outcome.jobs().get(1);
        assertThat(second.getSourceUrl()).isEmpty();
        assertThat(second.getDescription()).contains("未自动抓取详情页");
    }

    @Test
    void supportsAlternateCardSelector() {
        String html = """
                <div class="job-card-box"><span class="job-name">Java 工程师</span>
                <h3 class="company-name">测试公司</h3><span class="salary">15-20K</span>
                <div class="job-limit"><span>1-3年</span><span>本科</span></div></div>
                """;
        ParseOutcome outcome = parser.parse(html, "北京");
        assertThat(outcome.jobs()).hasSize(1);
        assertThat(outcome.jobs().get(0).getCity()).isEqualTo("北京");
    }

    @Test
    void discoversCardsFromJobDetailLinksWhenLegacyClassesAreAbsent() {
        String html = """
                <div class="search-results"><article>
                <a href="/job_detail/new-layout-001.html?lid=abc"><div class="job-title">
                <span class="custom-job-name">后端开发工程师</span></div></a>
                <span class="salary-text">20-30K</span><div class="company-info"><a>新锐测试公司</a></div>
                </article></div>
                """;

        ParseOutcome outcome = parser.parse(html, "北京");

        assertThat(outcome.jobs()).hasSize(1);
        JobPosting job = outcome.jobs().get(0);
        assertThat(job.getTitle()).isEqualTo("后端开发工程师");
        assertThat(job.getCompany()).isEqualTo("新锐测试公司");
        assertThat(job.getSalary()).isEqualTo("20-30K");
        assertThat(job.getCity()).isEqualTo("北京");
        assertThat(job.getSourceUrl()).isEqualTo("https://www.zhipin.com/job_detail/new-layout-001.html");
    }

    @Test
    void rejectsVerificationAndEmptyPages() {
        assertThatThrownBy(() -> parser.parse("<html><body>请完成安全验证</body></html>", "杭州"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("验证页");
        assertThatThrownBy(() -> parser.parse("<html><body>没有职位</body></html>", "杭州"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("未识别到职位卡片");
    }
}
