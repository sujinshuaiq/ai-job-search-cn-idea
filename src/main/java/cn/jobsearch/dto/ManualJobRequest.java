package cn.jobsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualJobRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String company,
        @Size(max = 80) String city,
        @Size(max = 120) String district,
        @Size(max = 80) String salary,
        @Size(max = 80) String experienceRequirement,
        @Size(max = 80) String educationRequirement,
        @Size(max = 1000) String sourceUrl,
        @Size(max = 1000) String tags,
        @NotBlank @Size(max = 30000) String description
) {
}
