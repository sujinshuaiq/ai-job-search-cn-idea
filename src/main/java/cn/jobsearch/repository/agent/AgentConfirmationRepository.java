package cn.jobsearch.repository.agent;

import cn.jobsearch.domain.agent.AgentConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConfirmationRepository extends JpaRepository<AgentConfirmation, Long> {
}
