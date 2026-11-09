package cn.jobsearch.service.crawler;

import cn.jobsearch.domain.JobPosting;

import java.util.List;

public record ParseOutcome(List<JobPosting> jobs, List<String> warnings) {
}
