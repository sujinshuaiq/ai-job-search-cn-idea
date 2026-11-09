package cn.jobsearch.dto;

import java.time.LocalDateTime;

public record ApiError(String code, String message, LocalDateTime timestamp) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, LocalDateTime.now());
    }
}
