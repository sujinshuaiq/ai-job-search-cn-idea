package cn.jobsearch.controller;

import cn.jobsearch.dto.BossImportRequest;
import cn.jobsearch.dto.BossSearchRequest;
import cn.jobsearch.dto.ImportResult;
import cn.jobsearch.service.crawler.BossCrawlerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crawler/boss")
public class CrawlerController {
    private final BossCrawlerService service;

    public CrawlerController(BossCrawlerService service) { this.service = service; }

    @PostMapping("/search")
    public ImportResult search(@Valid @RequestBody BossSearchRequest request) { return service.search(request); }

    @PostMapping("/import")
    public ImportResult importHtml(@Valid @RequestBody BossImportRequest request) { return service.importHtml(request); }
}
