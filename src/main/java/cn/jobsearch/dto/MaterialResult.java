package cn.jobsearch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MaterialResult {
    private String tailoredResume = "";
    private String greeting = "";
    private List<String> interviewQuestions = new ArrayList<>();

    public String getTailoredResume() { return tailoredResume; }
    public void setTailoredResume(String tailoredResume) { this.tailoredResume = tailoredResume; }
    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }
    public List<String> getInterviewQuestions() { return interviewQuestions; }
    public void setInterviewQuestions(List<String> interviewQuestions) { this.interviewQuestions = interviewQuestions; }
}
