package cn.jobsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.crawler")
public class CrawlerProperties {
    private boolean onlineEnabled = false;
    private String userAgent = "JobSearchCN/1.0 (robots.txt aware)";
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 20000;
    private long minIntervalMs = 3000;
    private int maxHtmlChars = 2000000;

    public boolean isOnlineEnabled() { return onlineEnabled; }
    public void setOnlineEnabled(boolean onlineEnabled) { this.onlineEnabled = onlineEnabled; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public long getMinIntervalMs() { return minIntervalMs; }
    public void setMinIntervalMs(long minIntervalMs) { this.minIntervalMs = minIntervalMs; }
    public int getMaxHtmlChars() { return maxHtmlChars; }
    public void setMaxHtmlChars(int maxHtmlChars) { this.maxHtmlChars = maxHtmlChars; }
}
