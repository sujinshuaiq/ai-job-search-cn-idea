package cn.jobsearch.service.crawler;

import cn.jobsearch.config.CrawlerProperties;
import cn.jobsearch.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class BossHttpClient {
    private final CrawlerProperties properties;
    private final HttpClient client;
    private long lastRequestNanos;

    public BossHttpClient(CrawlerProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public synchronized String get(URI uri) {
        validate(uri);
        awaitRateLimit();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("User-Agent", properties.getUserAgent())
                .header("Accept", "text/html,text/plain;q=0.9")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            lastRequestNanos = System.nanoTime();
            int status = response.statusCode();
            if (status == 404) return "";
            if (status == 429) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CRAWLER_RATE_LIMITED", "站点返回 429，已停止请求；请稍后手动重试，不会自动绕过限流");
            }
            if (status == 401 || status == 403) {
                throw new ApiException(HttpStatus.FORBIDDEN, "CRAWLER_ACCESS_DENIED", "站点拒绝自动访问（HTTP " + status + "），请改用离线 HTML 导入");
            }
            if (status >= 300 && status < 400) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "CRAWLER_REDIRECT", "站点要求跳转，可能进入登录或验证流程；已停止请求");
            }
            if (status < 200 || status >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "CRAWLER_HTTP_ERROR", "站点返回 HTTP " + status);
            }
            String body = response.body();
            if (body.length() > properties.getMaxHtmlChars()) {
                throw ApiException.badRequest("HTML_TOO_LARGE", "响应内容超过本地解析上限");
            }
            return body;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "CRAWLER_INTERRUPTED", "采集请求被中断");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CRAWLER_NETWORK_ERROR", "无法连接招聘站，请检查网络或改用离线 HTML 导入");
        }
    }

    private void validate(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"www.zhipin.com".equalsIgnoreCase(uri.getHost())) {
            throw ApiException.badRequest("UNSAFE_CRAWLER_URL", "只允许访问 https://www.zhipin.com 的公开地址");
        }
    }

    private void awaitRateLimit() {
        if (lastRequestNanos == 0) return;
        long elapsedMs = (System.nanoTime() - lastRequestNanos) / 1_000_000;
        long sleepMs = properties.getMinIntervalMs() - elapsedMs;
        if (sleepMs <= 0) return;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "CRAWLER_INTERRUPTED", "采集限速等待被中断");
        }
    }
}
