package cn.jobsearch.service.agent.tool;

import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.dto.AgentAnalysisResponse;
import cn.jobsearch.dto.AgentMaterialsResponse;
import cn.jobsearch.service.JobService;
import cn.jobsearch.service.agent.AgentExecutionGuard;
import cn.jobsearch.service.agent.AgentRunRecorder;
import cn.jobsearch.service.agent.AgentService;
import cn.jobsearch.service.agent.PromptSanitizer;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobAgentTools extends AgentToolSupport {
    private final JobService jobs;
    private final AgentService agentService;
    private final PromptSanitizer sanitizer;

    public JobAgentTools(JobService jobs, AgentService agentService, PromptSanitizer sanitizer,
                         AgentExecutionGuard guard, AgentRunRecorder recorder) {
        super(guard, recorder);
        this.jobs = jobs;
        this.agentService = agentService;
        this.sanitizer = sanitizer;
    }

    @Tool(name = "search_saved_jobs", description = "从本机职位库筛选职位。适合先找已采集职位，不访问互联网。关键词和城市可省略。")
    public List<JobToolView> searchJobs(
            @ToolParam(required = false, description = "职位、公司或技能关键词") String keyword,
            @ToolParam(required = false, description = "城市") String city,
            @ToolParam(required = false, description = "最多返回数量，1到10，默认5") Integer maxItems,
            ToolContext context) {
        return execute(context, "search_saved_jobs", () -> {
            int limit = maxItems == null ? 5 : Math.max(1, Math.min(10, maxItems));
            List<JobToolView> result = jobs.list(keyword, city, null, null).stream().limit(limit).map(JobToolView::from).toList();
            return new ToolOutcome<>(result, "职位库筛选出 " + result.size() + " 个职位");
        });
    }

    @Tool(name = "get_saved_job", description = "按数据库 ID 查看职位真实详情，不访问详情页。")
    public JobDetailView getJob(@ToolParam(description = "职位数据库 ID") Long jobId, ToolContext context) {
        return execute(context, "get_saved_job", () -> {
            JobPosting job = jobs.get(jobId);
            return new ToolOutcome<>(JobDetailView.from(job, sanitizer), "已读取职位 " + jobId + "：" + job.getTitle());
        });
    }

    @Tool(name = "analyze_saved_job", description = "分析一个已存在职位与本人档案的匹配度并保存结果。只有用户目标要求分析时才调用。")
    public AgentAnalysisResponse analyzeJob(@ToolParam(description = "职位数据库 ID") Long jobId, ToolContext context) {
        return execute(context, "analyze_saved_job", () -> {
            AgentAnalysisResponse result = agentService.analyze(jobId);
            return new ToolOutcome<>(result, "职位 " + jobId + " 分析完成，匹配分 " + result.matchScore());
        });
    }

    @Tool(name = "generate_job_materials", description = "为一个已存在职位生成并保存定向简历、沟通话术和面试题。只有用户明确要求时才调用。")
    public MaterialToolView materials(@ToolParam(description = "职位数据库 ID") Long jobId, ToolContext context) {
        return execute(context, "generate_job_materials", () -> {
            AgentMaterialsResponse result = agentService.materials(jobId);
            MaterialToolView view = new MaterialToolView(jobId, result.mode(), result.greeting(), result.interviewQuestions());
            return new ToolOutcome<>(view, "职位 " + jobId + " 的定向材料已生成并保存");
        });
    }

    public record JobToolView(Long id, String title, String company, String city, String salary,
                              String source, Integer matchScore) {
        static JobToolView from(JobPosting job) {
            return new JobToolView(job.getId(), job.getTitle(), job.getCompany(), job.getCity(), job.getSalary(),
                    job.getSource(), job.getMatchScore());
        }
    }

    public record JobDetailView(Long id, String title, String company, String city, String salary,
                                String experienceRequirement, String educationRequirement, String tags,
                                String description) {
        static JobDetailView from(JobPosting job, PromptSanitizer sanitizer) {
            return new JobDetailView(job.getId(), job.getTitle(), job.getCompany(), job.getCity(), job.getSalary(),
                    job.getExperienceRequirement(), job.getEducationRequirement(), job.getTags(),
                    sanitizer.clean(job.getDescription(), 5000));
        }
    }

    public record MaterialToolView(Long jobId, String mode, String greeting, List<String> interviewQuestions) {
    }
}
