package cn.jobsearch.dto;

public record BrowserStatusResponse(
        boolean running,
        boolean pageAvailable,
        boolean headless,
        String channel,
        String profileDirectory,
        String currentUrl,
        String currentTitle,
        boolean onBossDomain,
        boolean manualActionRequired,
        String message) {
}
