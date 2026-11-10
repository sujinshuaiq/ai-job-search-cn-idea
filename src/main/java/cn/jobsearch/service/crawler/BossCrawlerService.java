package cn.jobsearch.service.crawler;

import cn.jobsearch.config.CrawlerProperties;
import cn.jobsearch.dto.BossImportRequest;
import cn.jobsearch.dto.BossSearchRequest;
import cn.jobsearch.dto.ImportResult;
import cn.jobsearch.service.ApiException;
import cn.jobsearch.service.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class BossCrawlerService {
    private final BossHtmlParser parser;
    private final RobotsTxtService robotsTxtService;
    private final BossHttpClient httpClient;
    private final JobService jobService;
    private final CrawlerProperties properties;

    public BossCrawlerService(BossHtmlParser parser, RobotsTxtService robotsTxtService,
                              BossHttpClient httpClient, JobService jobService, CrawlerProperties properties) {
        this.parser = parser;
        this.robotsTxtService = robotsTxtService;
        this.httpClient = httpClient;
        this.jobService = jobService;
        this.properties = properties;
    }

    public ImportResult search(BossSearchRequest request) {
        if (!properties.isOnlineEnabled()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "BOSS_ONLINE_DISABLED",
                    "BOSS 在线采集默认关闭，未检查 robots、未发起任何网络请求。请使用离线 HTML 导入或手动录入；"
                            + "如你明确了解账号风险并确认有权访问，可设置 BOSS_ONLINE_ENABLED=true 后重启。开启也不代表可以避免封号。");
        }
        String query = URLEncoder.encode(request.query().trim(), StandardCharsets.UTF_8);
        URI uri = URI.create("https://www.zhipin.com/web/geek/job?query=" + query
                + "&city=" + request.cityCode() + "&page=" + request.page());
        robotsTxtService.assertAllowed(uri);
        String html = httpClient.get(uri);
        ParseOutcome outcome = parser.parse(html, request.city());
        return jobService.importJobs(outcome.jobs(), "BOSS_ONLINE_ROBOTS_CHECKED", outcome.warnings());
    }

    public ImportResult importHtml(BossImportRequest request) {
        ParseOutcome outcome = parser.parse(request.html(), request.city());
        return jobService.importJobs(outcome.jobs(), "BOSS_OFFLINE_HTML", outcome.warnings());
    }
}
