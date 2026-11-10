package cn.jobsearch.controller;

import cn.jobsearch.dto.agent.AgentConfirmationRequest;
import cn.jobsearch.dto.agent.AgentRunRequest;
import cn.jobsearch.dto.agent.AgentRunResponse;
import cn.jobsearch.service.agent.JobAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/runs")
public class AgentRunController {
    private final JobAgentService agent;

    public AgentRunController(JobAgentService agent) {
        this.agent = agent;
    }

    @PostMapping
    public AgentRunResponse start(@Valid @RequestBody AgentRunRequest request) {
        return agent.start(request);
    }

    @GetMapping("/{runId}")
    public AgentRunResponse get(@PathVariable Long runId) {
        return agent.get(runId);
    }

    @PostMapping("/{runId}/confirm")
    public AgentRunResponse confirm(@PathVariable Long runId, @Valid @RequestBody AgentConfirmationRequest request) {
        return agent.confirm(runId, request);
    }

    @PostMapping("/{runId}/cancel")
    public AgentRunResponse cancel(@PathVariable Long runId) {
        return agent.cancel(runId);
    }
}
