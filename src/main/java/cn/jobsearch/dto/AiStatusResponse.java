package cn.jobsearch.dto;

public record AiStatusResponse(
        boolean configured,
        String mode,
        String provider,
        String model,
        String baseUrl,
        String message
) {
}
