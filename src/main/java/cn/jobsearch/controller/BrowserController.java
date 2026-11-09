package cn.jobsearch.controller;

import cn.jobsearch.dto.BrowserCollectRequest;
import cn.jobsearch.dto.BrowserCollectionResponse;
import cn.jobsearch.dto.BrowserIntentRequest;
import cn.jobsearch.dto.BrowserIntentResponse;
import cn.jobsearch.dto.BrowserOpenRequest;
import cn.jobsearch.dto.BrowserStatusResponse;
import cn.jobsearch.dto.ImportResult;
import cn.jobsearch.service.JobService;
import cn.jobsearch.service.browser.BossSearchIntentService;
import cn.jobsearch.service.browser.BrowserAutomationService;
import cn.jobsearch.service.browser.CurrentPageJobs;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/browser/boss")
public class BrowserController {
    private final BrowserAutomationService browserService;
    private final BossSearchIntentService intentService;
    private final JobService jobService;

    public BrowserController(BrowserAutomationService browserService, BossSearchIntentService intentService,
                             JobService jobService) {
        this.browserService = browserService;
        this.intentService = intentService;
        this.jobService = jobService;
    }

    @PostMapping("/intent")
    public BrowserIntentResponse intent(@Valid @RequestBody BrowserIntentRequest request) {
        return intentService.parse(request);
    }

    @PostMapping("/open")
    public BrowserStatusResponse open(@RequestBody(required = false) BrowserOpenRequest request) {
        boolean jobsPage = request == null || !Boolean.FALSE.equals(request.jobsPage());
        Boolean headless = request == null ? null : request.headless();
        return browserService.openBoss(jobsPage, headless);
    }

    @GetMapping("/status")
    public BrowserStatusResponse status() {
        return browserService.status();
    }

    @PostMapping("/collect")
    public BrowserCollectionResponse collect(@Valid @RequestBody BrowserCollectRequest request) {
        CurrentPageJobs current = browserService.readCurrentJobs(request.city(), request.maxItems(),
                request.confirmedCurrentPage());
        ImportResult imported = jobService.importJobs(current.jobs(), "BOSS_BROWSER_CURRENT_PAGE", current.warnings());
        return new BrowserCollectionResponse(current.browser(), current.jobs().size(), current.jobs(),
                current.warnings(), imported);
    }

    @PostMapping("/close")
    public BrowserStatusResponse close() {
        return browserService.closeBrowser();
    }
}
