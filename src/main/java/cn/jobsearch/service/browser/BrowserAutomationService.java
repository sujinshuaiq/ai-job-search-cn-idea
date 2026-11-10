package cn.jobsearch.service.browser;

import cn.jobsearch.config.BrowserProperties;
import cn.jobsearch.dto.BrowserStatusResponse;
import cn.jobsearch.service.ApiException;
import cn.jobsearch.service.crawler.BossHtmlParser;
import cn.jobsearch.service.crawler.ParseOutcome;
import cn.jobsearch.service.crawler.RobotsTxtService;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

@Service
public class BrowserAutomationService {
    private static final URI BOSS_HOME = URI.create("https://www.zhipin.com/");
    private static final URI BOSS_JOBS = URI.create("https://www.zhipin.com/web/geek/job");

    private final BrowserProperties properties;
    private final RobotsTxtService robotsTxtService;
    private final BossHtmlParser parser;
    private final Path profilePath;
    private final ExecutorService browserExecutor;

    // Every field below is accessed only on browserExecutor's single thread.
    private Playwright playwright;
    private BrowserContext context;
    private Page currentPage;
    private boolean activeHeadless;
    private String activeChannel = "";

    public BrowserAutomationService(BrowserProperties properties, RobotsTxtService robotsTxtService,
                                    BossHtmlParser parser) {
        this.properties = properties;
        this.robotsTxtService = robotsTxtService;
        this.parser = parser;
        this.profilePath = Path.of(properties.getProfileDir()).toAbsolutePath().normalize();
        this.browserExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "boss-browser");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public BrowserStatusResponse start(Boolean headless) {
        ensureEnabled();
        return call(() -> {
            ensureContext(headless);
            return buildStatus("浏览器已启动；下一步可打开 BOSS 基础职位页");
        });
    }

    public BrowserStatusResponse openBoss(boolean jobsPage, Boolean headless) {
        ensureEnabled();
        return call(() -> {
            ensureContext(headless);
            Page page = requirePage();
            if (isBossUrl(page.url())) {
                return buildStatus("已保留当前 BOSS 标签页，没有重新导航；请继续手动登录、验证和搜索");
            }
            Page existingBossPage = findOpenBossPage();
            if (existingBossPage != null) {
                currentPage = existingBossPage;
                return buildStatus("已切换到现有 BOSS 标签页，没有重新导航；请继续手动操作");
            }
            if (!"about:blank".equals(page.url())) page = context.newPage();
            currentPage = page;
            URI target = jobsPage ? BOSS_JOBS : BOSS_HOME;
            robotsTxtService.assertAllowed(target);
            try {
                page.navigate(target.toString(), new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(properties.getNavigationTimeoutMs()));
            } catch (PlaywrightException ex) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "BROWSER_NAVIGATION_FAILED",
                        "基础页导航未完成：" + safeMessage(ex) + "。不会自动重试或改用搜索 URL");
            }
            return buildStatus("已打开无搜索参数的基础页；请手动登录、验证并搜索");
        });
    }

    public BrowserStatusResponse status() {
        if (!properties.isEnabled()) {
            return stoppedStatus("默认离线安全模式：BOSS 浏览器自动操作已关闭，不会启动浏览器、刷新或请求 BOSS。请使用离线 HTML 导入或手动录入。");
        }
        return call(() -> buildStatus(null));
    }

    public CurrentPageJobs readCurrentJobs(String city, Integer requestedMaxItems, boolean userConfirmed) {
        if (!userConfirmed) {
            throw ApiException.badRequest("USER_CONFIRMATION_REQUIRED", "必须先由你手动搜索并明确确认当前页面");
        }
        int maxItems = requestedMaxItems == null ? 10 : requestedMaxItems;
        if (maxItems < 1 || maxItems > 20) {
            throw ApiException.badRequest("INVALID_MAX_ITEMS", "单次读取条数必须在 1–20 之间");
        }
        ensureEnabled();
        String defaultCity = city == null ? "" : city.trim();
        return call(() -> {
            Page page = requirePage();
            requireBossUrl(page.url());
            waitForCards(page);
            String bodyText;
            try {
                bodyText = readBodySnippet(page);
                if (containsManualChallenge(page.title() + " " + bodyText)) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MANUAL_ACTION_REQUIRED",
                            "当前页仍需要人工登录或安全验证；请你在浏览器中完成后，再重新确认采集");
                }
            } catch (PlaywrightException ex) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PAGE_NOT_READY",
                        "当前标签页仍在导航或刷新，暂时无法稳定读取。请停止重复点击打开，等职位列表稳定显示后再采集");
            }
            CardExtraction extraction = extractCards(page, maxItems);
            ParseOutcome outcome;
            try {
                outcome = parser.parse(extraction.html(), defaultCity);
            } catch (ApiException ex) {
                if ("NO_JOB_CARDS".equals(ex.getCode()) || "NO_VALID_JOBS".equals(ex.getCode())) {
                    throw ApiException.badRequest(ex.getCode(), "当前活动标签页没有解析出有效职位。请确认该标签肉眼可见职位列表，"
                            + "不要停留在首页、登录页或空白基础页；若搜索打开了新标签，请激活搜索结果标签后重试。"
                            + "仍失败时可使用“离线 HTML 导入”。原始原因：" + ex.getMessage());
                }
                throw ex;
            }
            List<String> warnings = new ArrayList<>(outcome.warnings());
            if (extraction.hasMore()) {
                warnings.add("当前页职位卡片超过 " + maxItems + " 个，仅读取前 " + maxItems + " 个；不会自动翻页");
            }
            return new CurrentPageJobs(buildStatus("已读取你确认的当前页"), outcome.jobs(), warnings,
                    extraction.html());
        });
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "BOSS_BROWSER_DISABLED",
                    "BOSS 浏览器自动操作默认关闭，未启动浏览器或访问网站。请使用离线 HTML 导入或手动录入；"
                            + "如你明确了解账号风险并确认有权操作，可设置 BOSS_BROWSER_ENABLED=true 后重启。模拟人工也不能避免封号。");
        }
    }

    public BrowserStatusResponse closeBrowser() {
        return call(() -> {
            closeResources();
            return stoppedStatus("浏览器已关闭；本地会话目录未删除");
        });
    }

    private void ensureContext(Boolean requestedHeadless) {
        boolean desiredHeadless = requestedHeadless == null ? properties.isHeadless() : requestedHeadless;
        if (context != null && activeHeadless == desiredHeadless) return;
        if (context != null) closeResources();
        try {
            Files.createDirectories(profilePath);
            playwright = Playwright.create(new Playwright.CreateOptions().setEnv(java.util.Map.of(
                    "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1", "NO_COLOR", "1")));
            List<String> failures = new ArrayList<>();
            for (BrowserTarget target : candidateTargets()) {
                try {
                    var options = new com.microsoft.playwright.BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(desiredHeadless);
                    if (target.executablePath() == null) options.setChannel(target.channel());
                    else options.setExecutablePath(target.executablePath());
                    context = playwright.chromium().launchPersistentContext(profilePath, options);
                    activeHeadless = desiredHeadless;
                    activeChannel = target.channel();
                    currentPage = context.pages().stream().filter(page -> !page.isClosed()).findFirst()
                            .orElseGet(() -> context.newPage());
                    currentPage.setDefaultTimeout(properties.getNavigationTimeoutMs());
                    currentPage.setDefaultNavigationTimeout(properties.getNavigationTimeoutMs());
                    return;
                } catch (PlaywrightException ex) {
                    failures.add(target.channel() + ": " + safeMessage(ex));
                }
            }
            closeResources();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BROWSER_UNAVAILABLE",
                    "无法启动本机 Chrome/Edge。请安装浏览器，或设置 BOSS_BROWSER_EXECUTABLE_PATH 为浏览器 exe 绝对路径。"
                            + String.join("；", failures));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            closeResources();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BROWSER_UNAVAILABLE",
                    "浏览器运行环境初始化失败：" + safeMessage(ex));
        }
    }

    private List<BrowserTarget> candidateTargets() {
        String custom = properties.getExecutablePath() == null ? "" : properties.getExecutablePath().trim();
        if (!custom.isBlank()) {
            return List.of(new BrowserTarget("custom", Path.of(custom).toAbsolutePath().normalize()));
        }
        return candidateChannels().stream()
                .map(channel -> new BrowserTarget(channel, findSystemExecutable(channel)))
                .toList();
    }

    private List<String> candidateChannels() {
        String configured = properties.getChannel() == null ? "chrome" : properties.getChannel().trim();
        if (configured.isBlank() || configured.equalsIgnoreCase("auto")
                || configured.equalsIgnoreCase("chrome")) {
            return List.of("chrome", "msedge");
        }
        return List.of(configured);
    }

    private Path findSystemExecutable(String channel) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return null;
        String relative = switch (channel) {
            case "chrome" -> "Google\\Chrome\\Application\\chrome.exe";
            case "msedge" -> "Microsoft\\Edge\\Application\\msedge.exe";
            default -> "";
        };
        if (relative.isBlank()) return null;
        java.util.Set<String> roots = new java.util.LinkedHashSet<>();
        roots.add("C:\\Program Files");
        roots.add("C:\\Program Files (x86)");
        if (System.getenv("ProgramFiles") != null) roots.add(System.getenv("ProgramFiles"));
        if (System.getenv("ProgramFiles(x86)") != null) roots.add(System.getenv("ProgramFiles(x86)"));
        if (System.getenv("LOCALAPPDATA") != null) roots.add(System.getenv("LOCALAPPDATA"));
        for (String root : roots) {
            Path candidate = Path.of(root, relative);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private record BrowserTarget(String channel, Path executablePath) {
    }

    private Page requirePage() {
        if (context == null) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "BROWSER_NOT_RUNNING",
                    "浏览器尚未启动，请先调用启动工具或点击打开浏览器");
        }
        List<Page> openPages = context.pages().stream().filter(page -> !page.isClosed()).toList();
        if (openPages.isEmpty()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "BROWSER_PAGE_CLOSED",
                    "当前标签页已关闭，请重新打开 BOSS 基础职位页");
        }
        Page visiblePage = null;
        Page visibleBossPage = null;
        for (Page page : openPages) {
            if (!isVisible(page)) continue;
            visiblePage = page;
            if (isBossUrl(page.url())) visibleBossPage = page;
        }
        if (visibleBossPage != null) currentPage = visibleBossPage;
        else if (visiblePage != null) currentPage = visiblePage;
        else if (currentPage == null || currentPage.isClosed()) currentPage = openPages.get(openPages.size() - 1);
        return currentPage;
    }

    private boolean isVisible(Page page) {
        try {
            return "visible".equals(page.evaluate("() => document.visibilityState"));
        } catch (PlaywrightException ex) {
            return false;
        }
    }

    private Page findOpenBossPage() {
        List<Page> pages = context.pages();
        for (int index = pages.size() - 1; index >= 0; index--) {
            Page page = pages.get(index);
            if (!page.isClosed() && isBossUrl(page.url())) return page;
        }
        return null;
    }

    private BrowserStatusResponse buildStatus(String overrideMessage) {
        if (context == null) return stoppedStatus(overrideMessage == null ? "浏览器尚未启动" : overrideMessage);
        Page page;
        try {
            page = requirePage();
        } catch (ApiException ex) {
            return new BrowserStatusResponse(true, false, activeHeadless, activeChannel, profilePath.toString(),
                    "", "", false, true, "浏览器正在运行，但当前标签页不可用");
        }
        String url = page.url();
        String title = "";
        String body = "";
        try {
            title = page.title();
            body = readBodySnippet(page);
        } catch (PlaywrightException ignored) {
            // Status remains available even if the page is navigating at this instant.
        }
        boolean onBoss = isBossUrl(url);
        boolean manual = containsManualChallenge(title + " " + body) || !onBoss;
        String message = overrideMessage;
        if (message == null) {
            if (containsManualChallenge(title + " " + body)) {
                message = "请你在浏览器中人工完成登录或安全验证，然后手动搜索";
            } else if (onBoss) {
                message = "已识别当前活动 BOSS 标签页；确认看到职位列表后即可采集";
            } else {
                message = "浏览器已启动；当前不是 BOSS 页面，不能采集";
            }
        }
        return new BrowserStatusResponse(true, true, activeHeadless, activeChannel, profilePath.toString(),
                url, title, onBoss, manual, message);
    }

    private BrowserStatusResponse stoppedStatus(String message) {
        return new BrowserStatusResponse(false, false, properties.isHeadless(), properties.getChannel(),
                profilePath.toString(), "", "", false, false, message);
    }

    private void waitForCards(Page page) {
        try {
            page.waitForFunction("() => document.querySelector('.job-card-wrapper, .job-card-box, "
                            + ".job-list-box > li, a[href*='/job_detail/']')", null,
                    new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (PlaywrightException ignored) {
            // Parsing below returns a specific error instead of retrying or navigating again.
        }
    }

    private CardExtraction extractCards(Page page, int maxItems) {
        String script = """
                () => {
                  const selector = '.job-card-wrapper, .job-card-box, .job-list-box > li';
                  let cards = Array.from(document.querySelectorAll(selector));
                  if (!cards.length) {
                    cards = Array.from(document.querySelectorAll("a[href*='/job_detail/']"))
                      .map(link => link.closest(selector + ', article, li') || link.parentElement)
                      .filter(Boolean);
                  }
                  const unique = Array.from(new Set(cards));
                  return unique.filter(card => !unique.some(other => other !== card && other.contains(card)))
                    .slice(0, %d).map(element => element.outerHTML);
                }
                """.formatted(maxItems + 1);
        Object value = page.evaluate(script);
        List<String> cards = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) cards.add(item.toString());
            }
        }
        if (cards.isEmpty()) return new CardExtraction(page.content(), false);
        boolean hasMore = cards.size() > maxItems;
        List<String> selected = hasMore ? cards.subList(0, maxItems) : cards;
        String html = "<!doctype html><html><body><ul class=\"job-list-box\">"
                + String.join("\n", selected) + "</ul></body></html>";
        return new CardExtraction(html, hasMore);
    }

    private String readBodySnippet(Page page) {
        Object value = page.evaluate("() => document.body ? document.body.innerText.slice(0, 6000) : ''");
        return value == null ? "" : value.toString();
    }

    private boolean containsManualChallenge(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return text.contains("请先登录") || text.contains("登录后查看") || text.contains("扫码登录")
                || text.contains("请完成安全验证") || text.contains("滑动验证") || text.contains("安全验证")
                || lower.contains("captcha") || lower.contains("security-check");
    }

    static URI requireBossUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"www.zhipin.com".equalsIgnoreCase(uri.getHost())
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw ApiException.badRequest("UNEXPECTED_BROWSER_ORIGIN",
                        "当前标签页不是 https://www.zhipin.com，已拒绝读取");
            }
            return uri;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("UNEXPECTED_BROWSER_ORIGIN", "当前标签页地址无效，已拒绝读取");
        }
    }

    private boolean isBossUrl(String value) {
        try {
            requireBossUrl(value);
            return true;
        } catch (ApiException ex) {
            return false;
        }
    }

    private <T> T call(Callable<T> action) {
        try {
            return browserExecutor.submit(action).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "BROWSER_INTERRUPTED", "浏览器操作已中断");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ApiException apiException) throw apiException;
            if (cause instanceof PlaywrightException) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "BROWSER_OPERATION_FAILED",
                        "浏览器操作失败：" + safeMessage(cause));
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BROWSER_OPERATION_FAILED",
                    "浏览器操作失败：" + safeMessage(cause));
        } catch (RejectedExecutionException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BROWSER_SERVICE_CLOSED", "浏览器服务已关闭");
        }
    }

    private void closeResources() {
        currentPage = null;
        try {
            if (context != null) context.close();
        } finally {
            context = null;
            if (playwright != null) playwright.close();
            playwright = null;
            activeChannel = "";
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            closeBrowser();
        } catch (RuntimeException ignored) {
            // Preserve shutdown; the daemon thread and driver must not block application exit.
        } finally {
            browserExecutor.shutdownNow();
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "未知错误";
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record CardExtraction(String html, boolean hasMore) {
    }
}
