package cn.jobsearch.service.agent;

import org.springframework.stereotype.Component;

@Component
public class PromptSanitizer {

    public String clean(String value, int maxChars) {
        if (value == null) return "";
        String cleaned = value
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]", "")
                .trim();
        if (cleaned.length() <= maxChars) return cleaned;
        return cleaned.substring(0, maxChars) + "\n[内容过长，已截断]";
    }

    public String boundedList(String value) {
        return clean(value, 2000);
    }
}
