package cn.jobsearch.service;

import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.dto.ImportResult;
import cn.jobsearch.dto.ManualJobRequest;
import cn.jobsearch.repository.JobPostingRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class JobService {
    private final JobPostingRepository repository;
    private final JobKeyGenerator keyGenerator;

    public JobService(JobPostingRepository repository, JobKeyGenerator keyGenerator) {
        this.repository = repository;
        this.keyGenerator = keyGenerator;
    }

    @Transactional(readOnly = true)
    public List<JobPosting> list(String keyword, String city, String source, Boolean analyzed) {
        String q = lower(keyword);
        String cityFilter = lower(city);
        String sourceFilter = lower(source);
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(job -> q.isBlank() || lower(job.getTitle() + " " + job.getCompany() + " " + job.getTags() + " " + job.getDescription()).contains(q))
                .filter(job -> cityFilter.isBlank() || lower(job.getCity()).contains(cityFilter))
                .filter(job -> sourceFilter.isBlank() || lower(job.getSource()).equals(sourceFilter))
                .filter(job -> analyzed == null || (analyzed ? job.getMatchScore() != null : job.getMatchScore() == null))
                .toList();
    }

    @Transactional(readOnly = true)
    public JobPosting get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("职位不存在：" + id));
    }

    @Transactional
    public JobPosting createManual(ManualJobRequest request) {
        JobPosting job = new JobPosting();
        job.setTitle(request.title().trim());
        job.setCompany(request.company().trim());
        job.setCity(clean(request.city()));
        job.setDistrict(clean(request.district()));
        job.setSalary(clean(request.salary()));
        job.setExperienceRequirement(clean(request.experienceRequirement()));
        job.setEducationRequirement(clean(request.educationRequirement()));
        job.setSourceUrl(clean(request.sourceUrl()));
        job.setTags(clean(request.tags()));
        job.setDescription(request.description().trim());
        job.setSource("MANUAL");
        job.setExternalKey(keyGenerator.keyFor(job));
        if (repository.findByExternalKey(job.getExternalKey()).isPresent()) {
            throw ApiException.badRequest("DUPLICATE_JOB", "相同职位已存在，请勿重复录入");
        }
        return repository.save(job);
    }

    @Transactional
    public ImportResult importJobs(List<JobPosting> jobs, String mode, List<String> warnings) {
        if (jobs.size() > 100) {
            throw ApiException.badRequest("TOO_MANY_JOBS", "单次最多导入 100 个职位");
        }
        List<Long> ids = new ArrayList<>();
        int duplicates = 0;
        for (JobPosting job : jobs) {
            job.setExternalKey(keyGenerator.keyFor(job));
            if (repository.findByExternalKey(job.getExternalKey()).isPresent()) {
                duplicates++;
                continue;
            }
            ids.add(repository.save(job).getId());
        }
        return new ImportResult(jobs.size(), ids.size(), duplicates, ids, warnings, mode);
    }

    @Transactional
    public JobPosting save(JobPosting job) {
        return repository.save(job);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
