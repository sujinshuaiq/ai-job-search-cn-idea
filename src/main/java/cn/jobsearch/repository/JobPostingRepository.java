package cn.jobsearch.repository;

import cn.jobsearch.domain.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findByExternalKey(String externalKey);
    long countByMatchScoreIsNotNull();
    long countBySource(String source);
}
