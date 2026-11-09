package cn.jobsearch.service.crawler;

import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.service.ApiException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class BossHtmlParser {

    public ParseOutcome parse(String html, String defaultCity) {
        if (html == null || html.isBlank()) {
            throw ApiException.badRequest("EMPTY_HTML", "HTML 内容不能为空");
        }
        if (html.length() > 2_000_000) {
            throw ApiException.badRequest("HTML_TOO_LARGE", "HTML 不能超过 2 MB 字符");
        }
        Document document = Jsoup.parse(html, "https://www.zhipin.com");
        document.select("script,style,noscript,template,svg,iframe").remove();
        String visibleText = document.body() == null ? "" : document.body().text();
        if (containsVerification(visibleText)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFICATION_PAGE", "内容疑似登录或安全验证页，未进行解析；请使用正常浏览到的职位列表 HTML");
        }
        Elements cards = findCards(document);
        if (cards.isEmpty()) {
            throw ApiException.badRequest("NO_JOB_CARDS", "未识别到职位卡片。请保存完整搜索结果页，或检查页面结构是否已变化");
        }
        List<JobPosting> jobs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        for (Element card : cards) {
            if (jobs.size() >= 100) {
                warnings.add("已达到单次 100 个职位上限，其余卡片未导入");
                break;
            }
            JobPosting job = parseCard(card, defaultCity);
            if (job == null) {
                skipped++;
                continue;
            }
            jobs.add(job);
        }
        if (skipped > 0) warnings.add(skipped + " 个卡片缺少职位名称或公司，已跳过");
        if (jobs.isEmpty()) {
            throw ApiException.badRequest("NO_VALID_JOBS", "页面中未解析出有效职位，未写入数据库");
        }
        return new ParseOutcome(jobs, warnings);
    }

    private Elements findCards(Document document) {
        for (String selector : List.of(".job-card-wrapper", ".job-card-box", "li.job-card-wrapper", ".job-list-box > li")) {
            Elements cards = document.select(selector);
            if (!cards.isEmpty()) return cards;
        }
        Elements cards = new Elements();
        Set<Element> seen = new LinkedHashSet<>();
        for (Element link : document.select("a[href*='/job_detail/']")) {
            Element card = link.closest(".job-card-wrapper, .job-card-box, article, li");
            if (card == null) card = link.parent();
            if (card != null && seen.add(card)) cards.add(card);
        }
        return cards;
    }

    private JobPosting parseCard(Element card, String defaultCity) {
        String title = firstText(card, ".job-name", ".job-title-text", "span.job-name", "[class*='job-name']",
                ".job-title [class*='name']");
        String company = firstText(card, ".company-name", ".boss-name", ".company-info h3",
                "[class*='company-name']", ".company-info a");
        if (title.isBlank() || company.isBlank()) return null;

        JobPosting job = new JobPosting();
        job.setTitle(clean(title, 200));
        job.setCompany(clean(company, 200));
        String location = firstText(card, ".job-area", ".job-location", ".work-address", "[class*='job-area']");
        job.setCity(clean(location.isBlank() ? defaultCity : location, 80));
        job.setDistrict(clean(location, 120));
        job.setSalary(firstText(card, ".salary", ".job-salary", ".salary-range", "[class*='salary']"));
        job.setSource("BOSS");
        job.setSourceUrl(extractJobUrl(card));

        List<String> labels = textList(card, ".job-limit span", ".job-card-footer .tag-list li", ".tag-list li", ".job-tags span");
        job.setExperienceRequirement(labels.stream().filter(this::looksLikeExperience).findFirst().orElse(""));
        job.setEducationRequirement(labels.stream().filter(this::looksLikeEducation).findFirst().orElse(""));
        Set<String> tags = new LinkedHashSet<>(labels);
        tags.addAll(textList(card, ".labels span", ".job-card-footer .tag-list li", ".welfare-label span"));
        job.setTags(String.join("、", tags.stream().filter(tag -> !looksLikeExperience(tag) && !looksLikeEducation(tag)).limit(20).toList()));

        String footer = firstText(card, ".job-card-footer", ".job-description", ".job-detail");
        String description = footer.isBlank() ? card.text() : footer;
        job.setDescription(clean(description + "\n来源：用户提供的 BOSS 职位列表 HTML；未自动抓取详情页。", 30000));
        return job;
    }

    private String extractJobUrl(Element card) {
        Element link = card.selectFirst("a[href*='/job_detail/']");
        if (link == null) return "";
        try {
            URI uri = URI.create(link.absUrl("href"));
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equals("www.zhipin.com") || host.equals("zhipin.com"))) return "";
            return new URI("https", host, uri.getPath(), null, null).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String firstText(Element root, String... selectors) {
        for (String selector : selectors) {
            Element element = root.selectFirst(selector);
            if (element != null && !element.text().isBlank()) return clean(element.text(), 1000);
        }
        return "";
    }

    private List<String> textList(Element root, String... selectors) {
        Set<String> values = new LinkedHashSet<>();
        for (String selector : selectors) {
            for (Element element : root.select(selector)) {
                String value = clean(element.text(), 200);
                if (!value.isBlank()) values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    private boolean looksLikeExperience(String value) {
        return value.contains("年") || value.contains("经验") || value.equals("应届") || value.equals("在校");
    }

    private boolean looksLikeEducation(String value) {
        return List.of("中专", "高中", "大专", "本科", "硕士", "博士", "学历").stream().anyMatch(value::contains);
    }

    private boolean containsVerification(String text) {
        String lower = text.toLowerCase();
        return text.contains("请完成安全验证") || text.contains("滑动验证") || text.contains("请先登录")
                || lower.contains("captcha") || lower.contains("security-check");
    }

    private String clean(String value, int maxChars) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]", "")
                .replaceAll("[ \\t]+", " ").trim();
        return cleaned.length() <= maxChars ? cleaned : cleaned.substring(0, maxChars);
    }
}
