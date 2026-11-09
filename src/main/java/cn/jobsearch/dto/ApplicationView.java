package cn.jobsearch.dto;

import cn.jobsearch.domain.ApplicationRecord;
import cn.jobsearch.domain.ApplicationStatus;

import java.time.LocalDateTime;

public record ApplicationView(
        Long id,
        Long jobId,
        String jobTitle,
        String company,
        String city,
        String salary,
        ApplicationStatus status,
        String statusLabel,
        String note,
        LocalDateTime appliedAt,
        LocalDateTime updatedAt
) {
    public static ApplicationView from(ApplicationRecord record) {
        return new ApplicationView(
                record.getId(),
                record.getJob().getId(),
                record.getJob().getTitle(),
                record.getJob().getCompany(),
                record.getJob().getCity(),
                record.getJob().getSalary(),
                record.getStatus(),
                record.getStatus().getLabel(),
                record.getNote(),
                record.getAppliedAt(),
                record.getUpdatedAt()
        );
    }
}
