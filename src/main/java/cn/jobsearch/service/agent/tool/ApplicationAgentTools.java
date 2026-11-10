package cn.jobsearch.service.agent.tool;

import cn.jobsearch.dto.ApplicationView;
import cn.jobsearch.service.ApplicationService;
import cn.jobsearch.service.agent.AgentExecutionGuard;
import cn.jobsearch.service.agent.AgentRunRecorder;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApplicationAgentTools extends AgentToolSupport {
    private final ApplicationService applications;

    public ApplicationAgentTools(ApplicationService applications, AgentExecutionGuard guard, AgentRunRecorder recorder) {
        super(guard, recorder);
        this.applications = applications;
    }

    @Tool(name = "list_job_applications", description = "读取本人投递和收藏记录，不新增、不修改、不自动投递。")
    public List<ApplicationView> listApplications(
            @ToolParam(required = false, description = "最多返回数量，1到20，默认10") Integer maxItems,
            ToolContext context) {
        return execute(context, "list_job_applications", () -> {
            int limit = maxItems == null ? 10 : Math.max(1, Math.min(20, maxItems));
            List<ApplicationView> result = applications.list().stream().limit(limit).toList();
            return new ToolOutcome<>(result, "读取到 " + result.size() + " 条投递或收藏记录");
        });
    }
}
