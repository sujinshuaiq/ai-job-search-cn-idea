package cn.jobsearch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidate_profile")
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 120)
    private String currentTitle;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false)
    private int yearsOfExperience;

    @Column(length = 2000)
    private String skills;

    @Column(length = 1000)
    private String targetRoles;

    @Column(length = 80)
    private String expectedSalary;

    @Column(length = 500)
    private String education;

    @Lob
    @Column(nullable = false)
    private String experience;

    @Lob
    @Column(nullable = false)
    private String projectHighlights;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCurrentTitle() { return currentTitle; }
    public void setCurrentTitle(String currentTitle) { this.currentTitle = currentTitle; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public int getYearsOfExperience() { return yearsOfExperience; }
    public void setYearsOfExperience(int yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getTargetRoles() { return targetRoles; }
    public void setTargetRoles(String targetRoles) { this.targetRoles = targetRoles; }
    public String getExpectedSalary() { return expectedSalary; }
    public void setExpectedSalary(String expectedSalary) { this.expectedSalary = expectedSalary; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getProjectHighlights() { return projectHighlights; }
    public void setProjectHighlights(String projectHighlights) { this.projectHighlights = projectHighlights; }
}
