package cn.jobsearch.controller;

import cn.jobsearch.domain.CandidateProfile;
import cn.jobsearch.dto.ProfileUpdateRequest;
import cn.jobsearch.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService service;

    public ProfileController(ProfileService service) { this.service = service; }

    @GetMapping
    public CandidateProfile get() { return service.get(); }

    @PutMapping
    public CandidateProfile update(@Valid @RequestBody ProfileUpdateRequest request) { return service.update(request); }
}
