package cn.jobsearch.service.crawler;

import cn.jobsearch.config.CrawlerProperties;
import cn.jobsearch.dto.BossSearchRequest;
import cn.jobsearch.service.ApiException;
import cn.jobsearch.service.JobService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class BossCrawlerServiceTest {
    @Test
    void rejectsOnlineSearchBeforeRobotsOrHttpByDefault() {
        RobotsTxtService robots = mock(RobotsTxtService.class);
        BossHttpClient http = mock(BossHttpClient.class);
        JobService jobs = mock(JobService.class);
        BossCrawlerService service = new BossCrawlerService(new BossHtmlParser(), robots, http, jobs,
                new CrawlerProperties());

        assertThatThrownBy(() -> service.search(new BossSearchRequest("Java", "101210100", "杭州", 1)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("BOSS_ONLINE_DISABLED");
                    assertThat(ex.getMessage()).contains("未发起任何网络请求");
                });
        verifyNoInteractions(robots, http, jobs);
    }
}
