package cn.jobsearch.service;

import cn.jobsearch.domain.CandidateProfile;
import cn.jobsearch.dto.ProfileUpdateRequest;
import cn.jobsearch.repository.CandidateProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final CandidateProfileRepository repository;

    public ProfileService(CandidateProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CandidateProfile get() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> ApiException.notFound("候选人档案尚未初始化"));
    }

    @Transactional
    public CandidateProfile update(ProfileUpdateRequest request) {
        CandidateProfile profile = get();
        profile.setName(request.name().trim());
        profile.setCurrentTitle(request.currentTitle().trim());
        profile.setCity(request.city().trim());
        profile.setYearsOfExperience(request.yearsOfExperience());
        profile.setSkills(clean(request.skills()));
        profile.setTargetRoles(clean(request.targetRoles()));
        profile.setExpectedSalary(clean(request.expectedSalary()));
        profile.setEducation(clean(request.education()));
        profile.setExperience(request.experience().trim());
        profile.setProjectHighlights(request.projectHighlights().trim());
        return repository.save(profile);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
