package cn.jobsearch.repository.agent;

import cn.jobsearch.domain.agent.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {
}
