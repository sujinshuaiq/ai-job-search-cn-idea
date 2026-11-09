package cn.jobsearch.dto;

import cn.jobsearch.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplicationUpdateRequest(
        @NotNull ApplicationStatus status,
        @Size(max = 10000) String note
) {
}
