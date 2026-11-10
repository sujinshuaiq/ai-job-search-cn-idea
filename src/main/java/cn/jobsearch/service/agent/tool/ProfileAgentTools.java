package cn.jobsearch.service.agent.tool;

import cn.jobsearch.domain.CandidateProfile;
import cn.jobsearch.service.ProfileService;
import cn.jobsearch.service.agent.AgentExecutionGuard;
import cn.jobsearch.service.agent.AgentRunRecorder;
import cn.jobsearch.service.agent.PromptSanitizer;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ProfileAgentTools extends AgentToolSupport {
    private final ProfileService profiles;
    private final PromptSanitizer sanitizer;

    public ProfileAgentTools(ProfileService profiles, PromptSanitizer sanitizer,
                             AgentExecutionGuard guard, AgentRunRecorder recorder) {
        super(guard, recorder);
        this.profiles = profiles;
        this.sanitizer = sanitizer;
    }

    @Tool(name = "get_candidate_profile", description = "读取本人求职档案，用于匹配分析和材料生成。不得修改档案或虚构其中没有的事实。")
    public ProfileToolView getProfile(ToolContext context) {
        return execute(context, "get_candidate_profile", () -> {
            CandidateProfile profile = profiles.get();
            ProfileToolView view = new ProfileToolView(profile.getName(), profile.getCurrentTitle(), profile.getCity(),
                    profile.getYearsOfExperience(), profile.getSkills(), profile.getTargetRoles(), profile.getExpectedSalary(),
                    profile.getEducation(), sanitizer.clean(profile.getExperience(), 6000),
                    sanitizer.clean(profile.getProjectHighlights(), 6000));
            return new ToolOutcome<>(view, "已读取本人求职档案");
        });
    }

    public record ProfileToolView(String name, String currentTitle, String city, int yearsOfExperience,
                                  String skills, String targetRoles, String expectedSalary, String education,
                                  String experience, String projectHighlights) {
    }
}
