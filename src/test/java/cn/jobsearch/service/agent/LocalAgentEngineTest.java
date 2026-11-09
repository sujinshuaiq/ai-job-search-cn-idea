package cn.jobsearch.service.agent;

import cn.jobsearch.domain.CandidateProfile;
import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.dto.FitAnalysisResult;
import cn.jobsearch.dto.MaterialResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAgentEngineTest {
    private final LocalAgentEngine engine = new LocalAgentEngine();

    @Test
    void scoresSkillsCityAndTargetRole() {
        CandidateProfile profile = profile();
        JobPosting job = new JobPosting();
        job.setTitle("AI 应用开发工程师"); job.setCompany("测试公司"); job.setCity("杭州");
        job.setExperienceRequirement("3-5年"); job.setTags("Java、Spring Boot、RAG");
        job.setDescription("要求 Java、Spring Boot、MySQL，了解 RAG 与微服务。");

        FitAnalysisResult result = engine.analyze(profile, job);
        assertThat(result.getMatchScore()).isBetween(75, 95);
        assertThat(result.getStrengths()).anyMatch(item -> item.contains("Java"));
        assertThat(result.getGaps()).anyMatch(item -> item.contains("MySQL") || item.contains("微服务"));
        assertThat(result.getSummary()).contains("不是 DeepSeek");
    }

    @Test
    void materialsReuseOnlyProvidedExperience() {
        MaterialResult result = engine.materials(profile(), new JobPosting() {{
            setTitle("Java 后端工程师"); setCompany("测试公司"); setCity("杭州");
            setDescription("Java、Spring Boot");
        }});
        assertThat(result.getTailoredResume()).contains("真实项目事实", "真实工作经历", "本地模板");
        assertThat(result.getGreeting()).contains("4 年");
        assertThat(result.getInterviewQuestions()).isNotEmpty();
    }

    private CandidateProfile profile() {
        CandidateProfile profile = new CandidateProfile();
        profile.setName("测试人"); profile.setCurrentTitle("Java 后端"); profile.setCity("杭州");
        profile.setYearsOfExperience(4); profile.setSkills("Java、Spring Boot、RAG");
        profile.setTargetRoles("Java 后端、AI 应用开发"); profile.setEducation("本科");
        profile.setExperience("真实工作经历"); profile.setProjectHighlights("真实项目事实");
        return profile;
    }
}
