package cn.jobsearch.controller;

import cn.jobsearch.domain.JobPosting;
import cn.jobsearch.dto.ManualJobRequest;
import cn.jobsearch.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService service;

    public JobController(JobService service) { this.service = service; }

    @GetMapping
    public List<JobPosting> list(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String city,
                                 @RequestParam(required = false) String source,
                                 @RequestParam(required = false) Boolean analyzed) {
        return service.list(keyword, city, source, analyzed);
    }

    @GetMapping("/{id}")
    public JobPosting get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public JobPosting create(@Valid @RequestBody ManualJobRequest request) { return service.createManual(request); }
}
