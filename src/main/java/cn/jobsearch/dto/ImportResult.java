package cn.jobsearch.dto;

import java.util.List;

public record ImportResult(
        int parsed,
        int imported,
        int duplicates,
        List<Long> jobIds,
        List<String> warnings,
        String mode
) {
}
