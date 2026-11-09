package cn.jobsearch.dto;

import java.util.List;

public record AgentMaterialsResponse(
        Long jobId,
        String mode,
        String model,
        String tailoredResume,
        String greeting,
        List<String> interviewQuestions
) {
}
