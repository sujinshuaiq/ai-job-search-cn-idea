package cn.jobsearch.domain.agent;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_confirmation", indexes = @Index(name = "idx_agent_confirmation_run", columnList = "runId"))
public class AgentConfirmation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long runId;
    @Column(nullable = false, length = 60)
    private String action;
    @Lob @Column(nullable = false)
    private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ConfirmationStatus status;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public static AgentConfirmation pending(Long runId, String action, String payload, LocalDateTime expiresAt) {
        AgentConfirmation confirmation = new AgentConfirmation();
        confirmation.runId = runId;
        confirmation.action = action;
        confirmation.payload = payload;
        confirmation.status = ConfirmationStatus.PENDING;
        confirmation.expiresAt = expiresAt;
        confirmation.createdAt = LocalDateTime.now();
        return confirmation;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getAction() { return action; }
    public String getPayload() { return payload; }
    public ConfirmationStatus getStatus() { return status; }
    public void setStatus(ConfirmationStatus status) { this.status = status; this.resolvedAt = LocalDateTime.now(); }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
