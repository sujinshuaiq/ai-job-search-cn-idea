package cn.jobsearch.controller;

import cn.jobsearch.dto.AgentAnalysisResponse;
import cn.jobsearch.dto.AgentMaterialsResponse;
import cn.jobsearch.dto.AiStatusResponse;
import cn.jobsearch.service.agent.AgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AgentController {
    private final AgentService service;

    public AgentController(AgentService service) { this.service = service; }

    @GetMapping("/ai/status")
    public AiStatusResponse status() { return service.status(); }

    @PostMapping("/agent/jobs/{id}/analyze")
    public AgentAnalysisResponse analyze(@PathVariable Long id) { return service.analyze(id); }

    @PostMapping("/agent/jobs/{id}/materials")
    public AgentMaterialsResponse materials(@PathVariable Long id) { return service.materials(id); }
}
