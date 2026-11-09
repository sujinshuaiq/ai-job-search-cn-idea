package cn.jobsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrowserIntentRequest(
        @NotBlank @Size(max = 300) String text) {
}
