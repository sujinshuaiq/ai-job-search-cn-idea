package cn.jobsearch.service.agent;

import cn.jobsearch.config.AiProperties;
import cn.jobsearch.service.ApiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StructuredAiService {
    private final ChatClient chatClient;
    private final AiProperties properties;

    public StructuredAiService(ChatClient agentChatClient, AiProperties properties) {
        this.chatClient = agentChatClient;
        this.properties = properties;
    }

    public <T> T completeJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "AI_NOT_CONFIGURED", "未配置 DEEPSEEK_API_KEY");
        }
        try {
            T result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(responseType);
            if (result == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE", "DeepSeek 返回了空内容");
            }
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            String message = safeMessage(ex);
            if (message.contains("401") || message.contains("403")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_AUTHENTICATION_FAILED", "DeepSeek 鉴权失败，请检查 API Key");
            }
            if (message.contains("429")) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMITED", "DeepSeek 请求过于频繁或余额不足，请稍后重试");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_INVALID", "Spring AI 调用或结构化输出失败：" + message);
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
