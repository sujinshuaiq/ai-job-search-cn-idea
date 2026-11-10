package cn.jobsearch.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentAiConfig {
    @Bean
    ChatClient agentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
