package cn.jobsearch.repository;

import cn.jobsearch.domain.ApplicationRecord;
import cn.jobsearch.domain.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRecordRepository extends JpaRepository<ApplicationRecord, Long> {
    List<ApplicationRecord> findAllByOrderByUpdatedAtDesc();
    Optional<ApplicationRecord> findByJob_Id(Long jobId);
    long countByStatus(ApplicationStatus status);
}
