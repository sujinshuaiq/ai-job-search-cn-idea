package cn.jobsearch.service.agent;

import cn.jobsearch.service.agent.tool.ApplicationAgentTools;
import cn.jobsearch.service.agent.tool.BossBrowserAgentTools;
import cn.jobsearch.service.agent.tool.JobAgentTools;
import cn.jobsearch.service.agent.tool.ProfileAgentTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolSafetyTest {

    @Test
    void modelToolsAreLimitedToTheReviewedWhitelist() {
        Set<String> names = Arrays.stream(new Class<?>[]{
                        JobAgentTools.class, ProfileAgentTools.class,
                        ApplicationAgentTools.class, BossBrowserAgentTools.class})
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(method -> method.getAnnotation(Tool.class).name())
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder(
                "search_saved_jobs", "get_saved_job", "analyze_saved_job", "generate_job_materials",
                "get_candidate_profile", "list_job_applications",
                "open_boss_for_manual_search", "get_boss_browser_status");
        assertThat(names).noneMatch(name -> name.contains("collect") || name.contains("confirm")
                || name.contains("cookie") || name.contains("submit") || name.contains("application_status"));
    }

    @Test
    void bossCollectionCannotBeCalledAsAModelTool() {
        assertThat(Arrays.stream(BossBrowserAgentTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(Method::getName))
                .containsExactlyInAnyOrder("openForManualSearch", "status");
    }
}
