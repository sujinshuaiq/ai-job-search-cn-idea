package cn.jobsearch.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRunRequest(
        @NotBlank(message = "目标不能为空") @Size(max = 2000, message = "目标不能超过 2000 字") String goal,
        @Size(max = 36) String conversationId) {
}
