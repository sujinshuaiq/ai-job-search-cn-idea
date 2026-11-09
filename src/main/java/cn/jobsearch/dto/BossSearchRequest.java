package cn.jobsearch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BossSearchRequest(
        @NotBlank @Size(max = 100) String query,
        @NotBlank @Pattern(regexp = "\\d{6,12}", message = "城市编码格式不正确") String cityCode,
        @Size(max = 80) String city,
        @Min(1) @Max(3) int page
) {
}
