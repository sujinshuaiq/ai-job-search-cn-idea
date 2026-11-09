package cn.jobsearch.service.crawler;

import cn.jobsearch.dto.BossImportRequest;
import cn.jobsearch.dto.BossSearchRequest;
import cn.jobsearch.dto.ImportResult;
import cn.jobsearch.service.JobService;
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

    public BossCrawlerService(BossHtmlParser parser, RobotsTxtService robotsTxtService,
                              BossHttpClient httpClient, JobService jobService) {
        this.parser = parser;
        this.robotsTxtService = robotsTxtService;
        this.httpClient = httpClient;
        this.jobService = jobService;
    }

    public ImportResult search(BossSearchRequest request) {
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
