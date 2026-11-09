package cn.jobsearch.service.agent;

import cn.jobsearch.domain.CandidateProfile;
import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.dto.FitAnalysisResult;
import cn.jobsearch.dto.MaterialResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LocalAgentEngine {
    private static final List<String> COMMON_SKILLS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "MySQL", "Redis", "Kafka", "RocketMQ",
            "Docker", "Kubernetes", "Vue", "React", "Python", "LangChain", "RAG", "微服务"
    );

    public FitAnalysisResult analyze(CandidateProfile profile, JobPosting job) {
        List<String> skills = split(profile.getSkills());
        String corpus = lower(job.getTitle() + " " + job.getTags() + " " + job.getDescription());
        List<String> matched = skills.stream().filter(skill -> corpus.contains(lower(skill))).limit(8).toList();
        List<String> missing = COMMON_SKILLS.stream()
                .filter(skill -> corpus.contains(lower(skill)))
                .filter(skill -> !lower(profile.getSkills()).contains(lower(skill)))
                .limit(4)
                .toList();

        int score = 42 + Math.min(32, matched.size() * 8);
        if (containsIgnoreCase(job.getCity(), profile.getCity())) score += 8;
        if (matchesTargetRole(profile.getTargetRoles(), job.getTitle())) score += 10;
        int requiredYears = requiredYears(job.getExperienceRequirement() + " " + job.getDescription());
        if (requiredYears > profile.getYearsOfExperience()) score -= 12;
        if (matched.isEmpty()) score = Math.min(score, 62);
        score = Math.max(20, Math.min(95, score));

        FitAnalysisResult result = new FitAnalysisResult();
        result.setMatchScore(score);
        result.setSummary("本地规则根据技能覆盖、目标岗位、城市与年限估算；该结果不是 DeepSeek AI 分析。");
        List<String> strengths = new ArrayList<>();
        if (!matched.isEmpty()) strengths.add("已匹配技能：" + String.join("、", matched));
        if (containsIgnoreCase(job.getCity(), profile.getCity())) strengths.add("工作城市与求职偏好一致：" + profile.getCity());
        if (matchesTargetRole(profile.getTargetRoles(), job.getTitle())) strengths.add("职位名称符合目标方向：" + profile.getTargetRoles());
        if (strengths.isEmpty()) strengths.add("可结合项目经历进一步核对岗位职责");
        result.setStrengths(strengths);
        List<String> gaps = new ArrayList<>(missing.stream().map(skill -> "职位提及但档案未体现：" + skill + "（请人工确认是否为硬性要求）").toList());
        if (requiredYears > profile.getYearsOfExperience()) gaps.add("职位可能要求 " + requiredYears + " 年经验，档案为 " + profile.getYearsOfExperience() + " 年");
        if (gaps.isEmpty()) gaps.add("薪资、团队规模和具体业务方向仍需沟通确认");
        result.setGaps(gaps);
        return result;
    }

    public MaterialResult materials(CandidateProfile profile, JobPosting job) {
        FitAnalysisResult analysis = analyze(profile, job);
        MaterialResult result = new MaterialResult();
        String matchedSkills = analysis.getStrengths().get(0).replace("已匹配技能：", "");
        result.setTailoredResume(String.join("\n",
                "# " + profile.getName() + "｜应聘 " + job.getTitle(),
                "",
                "## 求职意向",
                job.getCity() + " · " + job.getCompany() + " · " + job.getTitle(),
                "",
                "## 个人概述",
                profile.getYearsOfExperience() + " 年经验，当前方向为" + profile.getCurrentTitle() + "。技能：" + profile.getSkills(),
                "",
                "## 教育背景",
                defaultText(profile.getEducation(), "请在个人档案中补充"),
                "",
                "## 工作经历（来自本人档案，未扩写事实）",
                profile.getExperience(),
                "",
                "## 代表项目（来自本人档案，未扩写事实）",
                profile.getProjectHighlights(),
                "",
                "## 岗位匹配重点",
                String.join("\n", analysis.getStrengths()),
                "",
                "> 本地模板仅整理已有档案，请投递前人工核对。"));
        result.setGreeting("您好，我有 " + profile.getYearsOfExperience() + " 年" + profile.getCurrentTitle()
                + "相关经验，关注到贵公司的「" + job.getTitle() + "」岗位。我的经历涉及 "
                + (matchedSkills.isBlank() ? profile.getSkills() : matchedSkills)
                + "，与该岗位有一定匹配。已根据职位要求整理简历，方便进一步沟通吗？");
        Set<String> questions = new LinkedHashSet<>();
        questions.add("请结合一个真实项目，说明你如何承担与「" + job.getTitle() + "」相关的核心职责？");
        questions.add("岗位描述中的关键技术要求是什么？请准备一项有数据支撑、但不能虚构指标的项目案例。");
        questions.add("如果现有经验与岗位要求存在缺口，你会如何在入职后 30 天内补齐？");
        for (String skill : split(profile.getSkills()).stream().limit(3).toList()) {
            questions.add("围绕 " + skill + "，说明你在实际项目中遇到的问题、排查过程和最终方案。");
        }
        result.setInterviewQuestions(new ArrayList<>(questions));
        return result;
    }

    private boolean matchesTargetRole(String targets, String title) {
        return split(targets).stream().anyMatch(role -> containsIgnoreCase(title, role));
    }

    private boolean containsIgnoreCase(String text, String part) {
        return text != null && part != null && !part.isBlank() && lower(text).contains(lower(part));
    }

    private int requiredYears(String text) {
        if (text == null) return 0;
        Matcher range = Pattern.compile("(\\d+)\\s*[-~—至]\\s*(\\d+)\\s*年").matcher(text);
        if (range.find()) return Integer.parseInt(range.group(1));
        Matcher single = Pattern.compile("(\\d+)\\s*年以上").matcher(text);
        return single.find() ? Integer.parseInt(single.group(1)) : 0;
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,，、;；\\n]+"))
                .map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
