package cn.jobsearch.dto;

import cn.jobsearch.domain.JobPosting;

import java.util.List;

public record BrowserCollectionResponse(
        BrowserStatusResponse browser,
        int parsed,
        List<JobPosting> jobs,
        List<String> warnings,
        ImportResult importResult) {
}
