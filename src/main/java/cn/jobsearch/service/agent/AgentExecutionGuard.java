package cn.jobsearch.service.agent;

import cn.jobsearch.config.AgentProperties;
import cn.jobsearch.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AgentExecutionGuard {
    private final AgentProperties properties;
    private final Map<Long, AtomicInteger> calls = new ConcurrentHashMap<>();

    public AgentExecutionGuard(AgentProperties properties) {
        this.properties = properties;
    }

    public void beforeTool(Long runId) {
        int count = calls.computeIfAbsent(runId, ignored -> new AtomicInteger()).incrementAndGet();
        if (count > properties.getMaxToolCalls()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AGENT_TOOL_BUDGET_EXCEEDED",
                    "本次任务工具调用已超过 " + properties.getMaxToolCalls() + " 次上限，请缩小目标或开始新任务");
        }
    }

    public void clear(Long runId) {
        calls.remove(runId);
    }
}
