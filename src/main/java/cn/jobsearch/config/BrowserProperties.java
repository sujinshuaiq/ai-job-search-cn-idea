package cn.jobsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.browser")
public class BrowserProperties {
    private boolean enabled = false;
    private String profileDir = "./data/browser-profile";
    private boolean headless = false;
    private String channel = "chrome";
    private String executablePath = "";
    private int navigationTimeoutMs = 45000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProfileDir() { return profileDir; }
    public void setProfileDir(String profileDir) { this.profileDir = profileDir; }
    public boolean isHeadless() { return headless; }
    public void setHeadless(boolean headless) { this.headless = headless; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getExecutablePath() { return executablePath; }
    public void setExecutablePath(String executablePath) { this.executablePath = executablePath; }
    public int getNavigationTimeoutMs() { return navigationTimeoutMs; }
    public void setNavigationTimeoutMs(int navigationTimeoutMs) { this.navigationTimeoutMs = navigationTimeoutMs; }
}
