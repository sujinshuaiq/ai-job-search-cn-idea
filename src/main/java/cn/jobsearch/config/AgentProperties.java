package cn.jobsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {
    private int maxToolCalls = 8;
    private int historyMessages = 24;
    private int confirmationTtlMinutes = 30;
    private int maxGoalChars = 2000;

    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = Math.max(1, Math.min(20, maxToolCalls)); }
    public int getHistoryMessages() { return historyMessages; }
    public void setHistoryMessages(int historyMessages) { this.historyMessages = Math.max(4, Math.min(100, historyMessages)); }
    public int getConfirmationTtlMinutes() { return confirmationTtlMinutes; }
    public void setConfirmationTtlMinutes(int confirmationTtlMinutes) { this.confirmationTtlMinutes = Math.max(1, Math.min(1440, confirmationTtlMinutes)); }
    public int getMaxGoalChars() { return maxGoalChars; }
    public void setMaxGoalChars(int maxGoalChars) { this.maxGoalChars = Math.max(100, Math.min(10000, maxGoalChars)); }
}
