package cn.jobsearch.service.agent.tool;

import cn.jobsearch.service.agent.AgentExecutionGuard;
import cn.jobsearch.service.agent.AgentRunRecorder;
import org.springframework.ai.chat.model.ToolContext;

import java.util.function.Supplier;

public abstract class AgentToolSupport {
    private final AgentExecutionGuard guard;
    private final AgentRunRecorder recorder;

    protected AgentToolSupport(AgentExecutionGuard guard, AgentRunRecorder recorder) {
        this.guard = guard;
        this.recorder = recorder;
    }

    protected <T> T execute(ToolContext context, String toolName, Supplier<ToolOutcome<T>> action) {
        long runId = runId(context);
        long started = System.nanoTime();
        try {
            guard.beforeTool(runId);
            ToolOutcome<T> outcome = action.get();
            recorder.record(runId, toolName, outcome.summary(), "SUCCESS", elapsed(started));
            return outcome.value();
        } catch (RuntimeException ex) {
            recorder.record(runId, toolName, safeMessage(ex), "FAILED", elapsed(started));
            throw ex;
        }
    }

    protected Long runId(ToolContext context) {
        Object value = context == null ? null : context.getContext().get("runId");
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("工具缺少有效的 runId 上下文");
    }

    private long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    protected record ToolOutcome<T>(T value, String summary) {
    }
}
