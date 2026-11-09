package cn.jobsearch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_posting", indexes = {
        @Index(name = "idx_job_company", columnList = "company"),
        @Index(name = "idx_job_city", columnList = "city"),
        @Index(name = "idx_job_created_at", columnList = "created_at")
})
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String externalKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(length = 80)
    private String city;

    @Column(length = 120)
    private String district;

    @Column(length = 80)
    private String salary;

    @Column(length = 80)
    private String experienceRequirement;

    @Column(length = 80)
    private String educationRequirement;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(length = 1000)
    private String sourceUrl;

    @Column(length = 1000)
    private String tags;

    @Lob
    private String description;

    private Integer matchScore;

    @Lob
    private String matchSummary;

    @Lob
    private String strengths;

    @Lob
    private String gaps;

    @Lob
    private String tailoredResume;

    @Lob
    private String greeting;

    @Lob
    private String interviewPrep;

    @Column(length = 30)
    private String analysisMode;

    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }
    public String getExperienceRequirement() { return experienceRequirement; }
    public void setExperienceRequirement(String experienceRequirement) { this.experienceRequirement = experienceRequirement; }
    public String getEducationRequirement() { return educationRequirement; }
    public void setEducationRequirement(String educationRequirement) { this.educationRequirement = educationRequirement; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getMatchScore() { return matchScore; }
    public void setMatchScore(Integer matchScore) { this.matchScore = matchScore; }
    public String getMatchSummary() { return matchSummary; }
    public void setMatchSummary(String matchSummary) { this.matchSummary = matchSummary; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getGaps() { return gaps; }
    public void setGaps(String gaps) { this.gaps = gaps; }
    public String getTailoredResume() { return tailoredResume; }
    public void setTailoredResume(String tailoredResume) { this.tailoredResume = tailoredResume; }
    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }
    public String getInterviewPrep() { return interviewPrep; }
    public void setInterviewPrep(String interviewPrep) { this.interviewPrep = interviewPrep; }
    public String getAnalysisMode() { return analysisMode; }
    public void setAnalysisMode(String analysisMode) { this.analysisMode = analysisMode; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
