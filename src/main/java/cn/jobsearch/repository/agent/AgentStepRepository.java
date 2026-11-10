package cn.jobsearch.repository.agent;

import cn.jobsearch.domain.agent.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentStepRepository extends JpaRepository<AgentStep, Long> {
    List<AgentStep> findByRunIdOrderByIdAsc(Long runId);
}
