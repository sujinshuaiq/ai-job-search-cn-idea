package cn.jobsearch.domain;

public enum ApplicationStatus {
    SAVED("已收藏"),
    APPLIED("已投递"),
    CHATTING("沟通中"),
    INTERVIEWING("面试中"),
    OFFER("Offer"),
    REJECTED("已拒绝"),
    CLOSED("已结束");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
