package cn.jobsearch.service;

import cn.jobsearch.domain.JobPosting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobKeyGeneratorTest {
    private final JobKeyGenerator generator = new JobKeyGenerator();

    @Test
    void normalizesUrlBeforeHashing() {
        JobPosting first = job("https://WWW.zhipin.com/job_detail/abc.html/?ka=one", "公司", "职位", "杭州");
        JobPosting second = job("https://www.zhipin.com/job_detail/abc.html?ka=two", "公司", "职位", "杭州");
        assertThat(generator.keyFor(first)).isEqualTo(generator.keyFor(second));
        assertThat(generator.keyFor(first)).hasSize(64);
    }

    @Test
    void compositeFallbackIsStableAndCityAware() {
        JobPosting first = job("", " 示例公司 ", "Java 工程师", "杭州");
        JobPosting second = job("", "示例公司", "Java 工程师", "杭州");
        JobPosting otherCity = job("", "示例公司", "Java 工程师", "上海");
        assertThat(generator.keyFor(first)).isEqualTo(generator.keyFor(second));
        assertThat(generator.keyFor(first)).isNotEqualTo(generator.keyFor(otherCity));
    }

    private JobPosting job(String url, String company, String title, String city) {
        JobPosting job = new JobPosting();
        job.setSourceUrl(url); job.setCompany(company); job.setTitle(title); job.setCity(city);
        return job;
    }
}
