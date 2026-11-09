package cn.jobsearch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 120) String currentTitle,
        @NotBlank @Size(max = 80) String city,
        @Min(0) @Max(60) int yearsOfExperience,
        @Size(max = 2000) String skills,
        @Size(max = 1000) String targetRoles,
        @Size(max = 80) String expectedSalary,
        @Size(max = 500) String education,
        @NotBlank @Size(max = 20000) String experience,
        @NotBlank @Size(max = 20000) String projectHighlights
) {
}
