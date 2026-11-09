package cn.jobsearch.dto;

import java.util.List;

public record DashboardResponse(
        long totalJobs,
        long analyzedJobs,
        long highMatchJobs,
        long applications,
        long interviews,
        long offers,
        List<JobSummary> topJobs,
        List<ApplicationView> recentApplications
) {
    public record JobSummary(Long id, String title, String company, String city, String salary, Integer matchScore) {
    }
}
