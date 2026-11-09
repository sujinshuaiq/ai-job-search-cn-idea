package cn.jobsearch.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record BrowserCollectRequest(
        @Size(max = 80) String city,
        @Min(1) @Max(20) Integer maxItems,
        @AssertTrue(message = "必须先人工搜索并确认当前页面") Boolean confirmedCurrentPage) {
}
