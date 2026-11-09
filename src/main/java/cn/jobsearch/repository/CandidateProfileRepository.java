package cn.jobsearch.repository;

import cn.jobsearch.domain.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {
    Optional<CandidateProfile> findFirstByOrderByIdAsc();
}
