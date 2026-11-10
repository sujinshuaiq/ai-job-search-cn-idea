package cn.jobsearch.dto.agent;

import cn.jobsearch.domain.agent.AgentRun;
import cn.jobsearch.domain.agent.AgentRunStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AgentRunResponse(Long id, String conversationId, String goal, AgentRunStatus status,
                               String mode, String response, Long pendingConfirmationId,
                               String pendingAction, List<AgentStepView> steps,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AgentRunResponse of(AgentRun run, List<AgentStepView> steps) {
        return new AgentRunResponse(run.getId(), run.getConversationId(), run.getGoal(), run.getStatus(),
                run.getMode(), run.getResponse(), run.getPendingConfirmationId(), run.getPendingAction(),
                steps, run.getCreatedAt(), run.getUpdatedAt());
    }
}
