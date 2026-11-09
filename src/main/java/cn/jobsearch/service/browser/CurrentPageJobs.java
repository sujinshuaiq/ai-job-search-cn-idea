package cn.jobsearch.service.browser;

import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.dto.BrowserStatusResponse;

import java.util.List;

public record CurrentPageJobs(
        BrowserStatusResponse browser,
        List<JobPosting> jobs,
        List<String> warnings,
        String cardHtml) {
}
