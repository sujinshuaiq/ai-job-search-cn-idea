package cn.jobsearch.dto;

import java.util.List;

public record BrowserIntentResponse(
        String query,
        String city,
        int maxItems,
        String mode,
        boolean requiresManualSearch,
        List<String> steps) {
}
