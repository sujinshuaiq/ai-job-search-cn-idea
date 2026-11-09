package cn.jobsearch.service;

import cn.jobsearch.domain.JobPosting;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class JobKeyGenerator {

    public String keyFor(JobPosting job) {
        String normalizedUrl = normalizeUrl(job.getSourceUrl());
        String raw = normalizedUrl.isBlank()
                ? "company-title-city|" + norm(job.getCompany()) + "|" + norm(job.getTitle()) + "|" + norm(job.getCity())
                : "url|" + normalizedUrl;
        return sha256(raw);
    }

    public String normalizeUrl(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isBlank()) return value.trim();
            int port = uri.getPort();
            String authority = host + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return new URI(scheme, authority, path, null, null).toString();
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256", ex);
        }
    }
}
