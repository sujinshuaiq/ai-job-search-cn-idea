package cn.jobsearch.config;

import cn.jobsearch.domain.ApplicationRecord;
import cn.jobsearch.domain.ApplicationStatus;
import cn.jobsearch.domain.CandidateProfile;
import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.repository.ApplicationRecordRepository;
import cn.jobsearch.repository.CandidateProfileRepository;
import cn.jobsearch.repository.JobPostingRepository;
import cn.jobsearch.service.JobKeyGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final CandidateProfileRepository profileRepository;
    private final JobPostingRepository jobRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final JobKeyGenerator keyGenerator;
    private final boolean seedEnabled;

    public DemoDataInitializer(CandidateProfileRepository profileRepository,
                               JobPostingRepository jobRepository,
                               ApplicationRecordRepository applicationRepository,
                               JobKeyGenerator keyGenerator,
                               @Value("${app.demo.seed-enabled:true}") boolean seedEnabled) {
        this.profileRepository = profileRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.keyGenerator = keyGenerator;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (profileRepository.findFirstByOrderByIdAsc().isEmpty()) {
            profileRepository.save(seedEnabled ? sampleProfile() : emptyProfile());
        }
        if (seedEnabled && jobRepository.count() == 0) {
            List<JobPosting> jobs = demoJobs().stream().map(this::toEntity).toList();
            jobRepository.saveAll(jobs);
            createApplication(jobs.get(0), ApplicationStatus.INTERVIEWING, "演示记录：已完成一轮技术面试，请替换为真实进展");
            createApplication(jobs.get(1), ApplicationStatus.APPLIED, "演示记录：等待 HR 反馈");
        }
    }

    private CandidateProfile sampleProfile() {
        CandidateProfile profile = emptyProfile();
        profile.setName("林晨（示例）");
        profile.setCurrentTitle("Java 后端开发工程师");
        profile.setCity("杭州");
        profile.setYearsOfExperience(4);
        profile.setSkills("Java、Spring Boot、Spring Cloud、MySQL、Redis、RocketMQ、Docker、Vue、Python、RAG");
        profile.setTargetRoles("Java 后端、AI 应用开发、大模型应用工程师");
        profile.setExpectedSalary("20-28K");
        profile.setEducation("本科 · 计算机科学与技术（示例档案，请修改）");
        profile.setExperience("某企业服务平台 · Java 后端开发（示例）\n负责订单、权限与消息模块开发；参与 MySQL 索引优化、Redis 缓存治理和 RocketMQ 异步解耦。此处全部为演示事实，请替换为本人真实经历。");
        profile.setProjectHighlights("智能客服知识库项目（示例）\n使用 Java、Python 与 RAG 完成知识检索服务联调，负责权限过滤、检索接口和效果评测。演示数据不包含真实绩效指标，请在个人档案中填写可核验成果。");
        return profile;
    }

    private CandidateProfile emptyProfile() {
        CandidateProfile profile = new CandidateProfile();
        profile.setName("请填写姓名");
        profile.setCurrentTitle("Java 开发工程师");
        profile.setCity("杭州");
        profile.setYearsOfExperience(0);
        profile.setSkills("Java");
        profile.setTargetRoles("Java 后端");
        profile.setExpectedSalary("面议");
        profile.setEducation("请填写真实教育背景");
        profile.setExperience("请填写本人真实工作经历");
        profile.setProjectHighlights("请填写本人真实项目经历与可核验成果");
        return profile;
    }

    private JobPosting toEntity(DemoJob demo) {
        JobPosting job = new JobPosting();
        job.setTitle(demo.title());
        job.setCompany(demo.company() + "（演示公司）");
        job.setCity(demo.city());
        job.setDistrict(demo.city() + " · 示例商圈");
        job.setSalary(demo.salary());
        job.setExperienceRequirement(demo.experience());
        job.setEducationRequirement("本科");
        job.setTags(demo.tags());
        job.setDescription(demo.description() + "\n\n注意：这是用于验证产品流程的虚构演示职位，不是真实招聘广告。");
        job.setSource("DEMO");
        job.setSourceUrl("");
        job.setExternalKey(keyGenerator.keyFor(job));
        return job;
    }

    private void createApplication(JobPosting job, ApplicationStatus status, String note) {
        ApplicationRecord record = new ApplicationRecord();
        record.setJob(job);
        record.setStatus(status);
        record.setNote(note);
        applicationRepository.save(record);
    }

    private List<DemoJob> demoJobs() {
        return List.of(
                new DemoJob("AI 应用开发工程师", "云帆智能", "杭州", "22-30K", "3-5年", "Java、Spring Boot、RAG、Python", "负责企业知识库与大模型应用后端，要求 Java、Spring Boot，了解 RAG 检索、提示词评测和服务性能优化。"),
                new DemoJob("Java 高级开发工程师", "山海互联", "杭州", "20-28K", "3-5年", "Java、Spring Cloud、MySQL、Redis", "负责交易与账户服务，使用 Spring Cloud、MySQL、Redis、RocketMQ，关注高并发、数据一致性和可观测性。"),
                new DemoJob("大模型平台后端工程师", "星河数据", "上海", "25-35K", "3-5年", "Java、Python、LangChain、RAG", "建设模型接入、Agent 工作流和评测平台，要求 Java 或 Python 后端经验，熟悉 LangChain、RAG 与微服务。"),
                new DemoJob("Java 微服务工程师", "青禾软件", "深圳", "18-26K", "1-3年", "Java、Spring Boot、Docker、Kafka", "参与业务微服务建设，要求 Spring Boot、MySQL、Kafka，了解 Docker 部署和分布式问题排查。"),
                new DemoJob("AI 产品工程师", "拾光科技", "北京", "20-32K", "3-5年", "Python、RAG、Vue、MySQL", "连接产品和算法团队，交付智能问答应用；要求 Python、RAG 基础，能够完成接口开发与效果分析。"),
                new DemoJob("后端开发工程师", "澄明网络", "广州", "16-24K", "1-3年", "Java、MySQL、Redis、Vue", "负责管理后台和业务 API，使用 Java、Spring Boot、MySQL，前端使用 Vue，重视代码质量与协作。"),
                new DemoJob("搜索推荐后端工程师", "白鹭信息", "杭州", "24-34K", "3-5年", "Java、Kafka、Redis、微服务", "负责搜索推荐在线服务与特征管道，要求 Java、Kafka、Redis 和微服务治理经验。"),
                new DemoJob("智能体应用工程师", "远山实验室", "上海", "24-36K", "1-3年", "Python、LangChain、RAG、Docker", "研发企业 Agent 工具调用和知识检索能力，要求 Python、LangChain、RAG，能够设计离线评测集。"),
                new DemoJob("Java 支付研发工程师", "金杉金融", "北京", "23-32K", "3-5年", "Java、Spring Cloud、RocketMQ、MySQL", "负责支付清结算服务，强调事务一致性、幂等、RocketMQ 与 MySQL，需具备风险意识。"),
                new DemoJob("全栈开发工程师", "木棉数字", "深圳", "18-27K", "1-3年", "Java、Vue、Docker、MySQL", "负责企业内部应用全栈交付，后端 Java、前端 Vue，使用 Docker 部署并维护 MySQL 数据模型。"),
                new DemoJob("中间件开发工程师", "北辰基础软件", "杭州", "25-38K", "5-7年", "Java、Kafka、RocketMQ、Kubernetes", "参与消息中间件与云原生控制面研发，要求深入 Java、Kafka 或 RocketMQ，熟悉 Kubernetes。"),
                new DemoJob("Java 开发实习生", "新芽教育", "广州", "4-6K", "经验不限", "Java、Spring Boot、MySQL", "参与学习平台功能开发和测试，要求 Java 基础与 MySQL，提供代码评审和导师指导。")
        );
    }

    private record DemoJob(String title, String company, String city, String salary,
                           String experience, String tags, String description) { }
}
