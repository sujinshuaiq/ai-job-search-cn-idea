package cn.jobsearch.service.agent;

import cn.jobsearch.domain.agent.AgentStep;
import cn.jobsearch.repository.agent.AgentStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunRecorder {
    private final AgentStepRepository steps;

    public AgentRunRecorder(AgentStepRepository steps) {
        this.steps = steps;
    }

    @Transactional
    public void record(Long runId, String toolName, String summary, String status, long durationMs) {
        steps.save(AgentStep.of(runId, toolName, summary, status, durationMs));
    }
}
