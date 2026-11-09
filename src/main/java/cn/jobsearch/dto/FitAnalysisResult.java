package cn.jobsearch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FitAnalysisResult {
    private int matchScore;
    private String summary = "";
    private List<String> strengths = new ArrayList<>();
    private List<String> gaps = new ArrayList<>();

    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }
    public List<String> getGaps() { return gaps; }
    public void setGaps(List<String> gaps) { this.gaps = gaps; }
}
