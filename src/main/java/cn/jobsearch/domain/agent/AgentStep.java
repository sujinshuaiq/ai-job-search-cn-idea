package cn.jobsearch.domain.agent;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_step", indexes = @Index(name = "idx_agent_step_run", columnList = "runId"))
public class AgentStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long runId;
    @Column(nullable = false, length = 100)
    private String toolName;
    @Column(nullable = false, length = 1500)
    private String summary;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private long durationMs;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static AgentStep of(Long runId, String toolName, String summary, String status, long durationMs) {
        AgentStep step = new AgentStep();
        step.runId = runId;
        step.toolName = toolName;
        step.summary = summary == null ? "" : summary.length() > 1500 ? summary.substring(0, 1500) : summary;
        step.status = status;
        step.durationMs = durationMs;
        step.createdAt = LocalDateTime.now();
        return step;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getToolName() { return toolName; }
    public String getSummary() { return summary; }
    public String getStatus() { return status; }
    public long getDurationMs() { return durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
