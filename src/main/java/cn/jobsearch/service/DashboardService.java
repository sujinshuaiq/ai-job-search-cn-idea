package cn.jobsearch.service;

import cn.jobsearch.domain.ApplicationStatus;
import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.dto.ApplicationView;
import cn.jobsearch.dto.DashboardResponse;
import cn.jobsearch.repository.ApplicationRecordRepository;
import cn.jobsearch.repository.JobPostingRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class DashboardService {
    private final JobPostingRepository jobRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationService applicationService;

    public DashboardService(JobPostingRepository jobRepository,
                            ApplicationRecordRepository applicationRepository,
                            ApplicationService applicationService) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.applicationService = applicationService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get() {
        List<JobPosting> jobs = jobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<DashboardResponse.JobSummary> topJobs = jobs.stream()
                .filter(job -> job.getMatchScore() != null)
                .sorted(Comparator.comparing(JobPosting::getMatchScore).reversed())
                .limit(5)
                .map(job -> new DashboardResponse.JobSummary(job.getId(), job.getTitle(), job.getCompany(), job.getCity(), job.getSalary(), job.getMatchScore()))
                .toList();
        List<ApplicationView> recent = applicationService.list().stream().limit(5).toList();
        return new DashboardResponse(
                jobs.size(),
                jobs.stream().filter(job -> job.getMatchScore() != null).count(),
                jobs.stream().filter(job -> job.getMatchScore() != null && job.getMatchScore() >= 80).count(),
                applicationRepository.count(),
                applicationRepository.countByStatus(ApplicationStatus.INTERVIEWING),
                applicationRepository.countByStatus(ApplicationStatus.OFFER),
                topJobs,
                recent
        );
    }
}
