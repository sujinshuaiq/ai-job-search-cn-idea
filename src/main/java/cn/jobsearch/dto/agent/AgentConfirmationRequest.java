package cn.jobsearch.dto.agent;

import jakarta.validation.constraints.NotNull;

public record AgentConfirmationRequest(@NotNull Long confirmationId, @NotNull Boolean approved) {
}
