package cn.jobsearch.controller;

import cn.jobsearch.dto.ApplicationRequest;
import cn.jobsearch.dto.ApplicationUpdateRequest;
import cn.jobsearch.dto.ApplicationView;
import cn.jobsearch.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) { this.service = service; }

    @GetMapping
    public List<ApplicationView> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView create(@Valid @RequestBody ApplicationRequest request) { return service.create(request); }

    @PatchMapping("/{id}")
    public ApplicationView update(@PathVariable Long id, @Valid @RequestBody ApplicationUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
