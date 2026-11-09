package cn.jobsearch.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AgentAnalysisResponse(
        Long jobId,
        String mode,
        String model,
        int matchScore,
        String summary,
        List<String> strengths,
        List<String> gaps,
        LocalDateTime analyzedAt
) {
}
