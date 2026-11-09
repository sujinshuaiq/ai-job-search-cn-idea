package cn.jobsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BossImportRequest(
        @NotBlank @Size(max = 2000000) String html,
        @Size(max = 80) String city
) {
}
