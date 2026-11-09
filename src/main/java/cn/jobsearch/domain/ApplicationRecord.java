package cn.jobsearch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_record")
public class ApplicationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private JobPosting job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    @Lob
    private String note;

    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        updatedAt = LocalDateTime.now();
        if (status == ApplicationStatus.APPLIED && appliedAt == null) {
            appliedAt = updatedAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (status == ApplicationStatus.APPLIED && appliedAt == null) {
            appliedAt = updatedAt;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public JobPosting getJob() { return job; }
    public void setJob(JobPosting job) { this.job = job; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
