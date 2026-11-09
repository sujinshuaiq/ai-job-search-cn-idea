package cn.jobsearch.service.agent;

import cn.jobsearch.config.AiProperties;
import cn.jobsearch.service.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public <T> T completeJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "AI_NOT_CONFIGURED", "未配置 DEEPSEEK_API_KEY");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "model", properties.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.2,
                    "max_tokens", properties.getMaxTokens(),
                    "stream", false
            );
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw mapHttpError(response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE", "DeepSeek 返回了空内容");
            }
            return objectMapper.readValue(stripCodeFence(content.asText()), responseType);
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_INTERRUPTED", "DeepSeek 请求被中断");
        } catch (IOException | IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_INVALID", "DeepSeek 调用或 JSON 解析失败：" + safeMessage(ex));
        }
    }

    private URI endpoint() {
        String base = properties.getBaseUrl().trim().replaceAll("/+$", "");
        if (base.endsWith("/chat/completions")) return URI.create(base);
        return URI.create(base + "/chat/completions");
    }

    private ApiException mapHttpError(int status) {
        if (status == 401 || status == 403) {
            return new ApiException(HttpStatus.BAD_GATEWAY, "AI_AUTHENTICATION_FAILED", "DeepSeek 鉴权失败，请检查 API Key");
        }
        if (status == 429) {
            return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMITED", "DeepSeek 请求过于频繁或余额不足，请稍后重试");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "AI_HTTP_ERROR", "DeepSeek 接口返回 HTTP " + status);
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
