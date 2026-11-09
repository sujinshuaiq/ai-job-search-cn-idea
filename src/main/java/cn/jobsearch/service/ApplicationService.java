package cn.jobsearch.service;

import cn.jobsearch.domain.ApplicationRecord;
import cn.jobsearch.domain.ApplicationStatus;
import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.dto.ApplicationRequest;
import cn.jobsearch.dto.ApplicationUpdateRequest;
import cn.jobsearch.dto.ApplicationView;
import cn.jobsearch.repository.ApplicationRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApplicationService {
    private final ApplicationRecordRepository repository;
    private final JobService jobService;

    public ApplicationService(ApplicationRecordRepository repository, JobService jobService) {
        this.repository = repository;
        this.jobService = jobService;
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(ApplicationView::from).toList();
    }

    @Transactional
    public ApplicationView create(ApplicationRequest request) {
        repository.findByJob_Id(request.jobId()).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_EXISTS", "该职位已在投递管理中");
        });
        JobPosting job = jobService.get(request.jobId());
        ApplicationRecord record = new ApplicationRecord();
        record.setJob(job);
        record.setStatus(request.status() == null ? ApplicationStatus.SAVED : request.status());
        record.setNote(request.note() == null ? "" : request.note().trim());
        if (record.getStatus() != ApplicationStatus.SAVED) {
            record.setAppliedAt(LocalDateTime.now());
        }
        return ApplicationView.from(repository.save(record));
    }

    @Transactional
    public ApplicationView update(Long id, ApplicationUpdateRequest request) {
        ApplicationRecord record = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("投递记录不存在：" + id));
        if (record.getAppliedAt() == null && request.status() != ApplicationStatus.SAVED) {
            record.setAppliedAt(LocalDateTime.now());
        }
        record.setStatus(request.status());
        record.setNote(request.note() == null ? "" : request.note().trim());
        return ApplicationView.from(repository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        ApplicationRecord record = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("投递记录不存在：" + id));
        repository.delete(record);
    }
}
