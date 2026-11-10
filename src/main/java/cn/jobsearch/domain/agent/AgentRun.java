package cn.jobsearch.domain.agent;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_run", indexes = @Index(name = "idx_agent_run_conversation", columnList = "conversationId"))
public class AgentRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 36)
    private String conversationId;
    @Lob @Column(nullable = false)
    private String goal;
    @Lob
    private String response;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private AgentRunStatus status;
    @Column(nullable = false, length = 30)
    private String mode;
    @Column(length = 60)
    private String pendingAction;
    private Long pendingConfirmationId;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static AgentRun create(String conversationId, String goal, String mode) {
        AgentRun run = new AgentRun();
        run.conversationId = conversationId;
        run.goal = goal;
        run.mode = mode;
        run.status = AgentRunStatus.RUNNING;
        run.createdAt = LocalDateTime.now();
        run.updatedAt = run.createdAt;
        return run;
    }

    public Long getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getGoal() { return goal; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public AgentRunStatus getStatus() { return status; }
    public void setStatus(AgentRunStatus status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getPendingAction() { return pendingAction; }
    public Long getPendingConfirmationId() { return pendingConfirmationId; }
    public void setPendingConfirmation(String action, Long confirmationId) { this.pendingAction = action; this.pendingConfirmationId = confirmationId; this.updatedAt = LocalDateTime.now(); }
    public void clearPendingConfirmation() { this.pendingAction = null; this.pendingConfirmationId = null; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
