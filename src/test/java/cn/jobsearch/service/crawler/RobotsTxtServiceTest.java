package cn.jobsearch.service.crawler;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtServiceTest {
    private final RobotsTxtService service = new RobotsTxtService(null);

    @Test
    void blocksBossSearchQueryAndCityParameters() {
        String robots = """
                User-agent: *
                Disallow: /*?query=*
                Disallow: *?city=*
                Disallow: /job_detail/l*.html
                """;
        RobotsTxtService.Decision search = service.evaluate(robots,
                URI.create("https://www.zhipin.com/web/geek/job?query=Java&city=101210100&page=1"));
        assertThat(search.allowed()).isFalse();
        assertThat(search.rule()).contains("query");

        RobotsTxtService.Decision detail = service.evaluate(robots,
                URI.create("https://www.zhipin.com/job_detail/abc.html"));
        assertThat(detail.allowed()).isTrue();

        RobotsTxtService.Decision blockedDetail = service.evaluate(robots,
                URI.create("https://www.zhipin.com/job_detail/l123.html"));
        assertThat(blockedDetail.allowed()).isFalse();
    }

    @Test
    void allowWinsWhenSpecificityIsEqual() {
        String robots = """
                User-agent: *
                Disallow: /web/geek/job
                Allow: /web/geek/job
                """;
        assertThat(service.evaluate(robots, URI.create("https://www.zhipin.com/web/geek/job")).allowed()).isTrue();
    }

    @Test
    void ignoresUnrelatedAgentGroup() {
        String robots = """
                User-agent: SemrushBot
                Disallow: /
                User-agent: *
                Disallow: /*?query=*
                """;
        assertThat(service.evaluate(robots, URI.create("https://www.zhipin.com/job_detail/abc.html")).allowed()).isTrue();
        assertThat(service.evaluate(robots, URI.create("https://www.zhipin.com/web/geek/job?query=Java")).allowed()).isFalse();
    }
}
