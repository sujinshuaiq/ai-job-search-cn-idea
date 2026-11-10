package cn.jobsearch.dto.agent;

import cn.jobsearch.domain.agent.AgentStep;

import java.time.LocalDateTime;

public record AgentStepView(Long id, String toolName, String summary, String status,
                            long durationMs, LocalDateTime createdAt) {
    public static AgentStepView from(AgentStep step) {
        return new AgentStepView(step.getId(), step.getToolName(), step.getSummary(), step.getStatus(),
                step.getDurationMs(), step.getCreatedAt());
    }
}
