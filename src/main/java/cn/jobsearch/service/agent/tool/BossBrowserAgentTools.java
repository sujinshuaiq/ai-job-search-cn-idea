package cn.jobsearch.service.agent.tool;

import cn.jobsearch.domain.agent.AgentConfirmation;
import cn.jobsearch.dto.BrowserStatusResponse;
import cn.jobsearch.service.agent.AgentConfirmationService;
import cn.jobsearch.service.agent.AgentExecutionGuard;
import cn.jobsearch.service.agent.AgentRunRecorder;
import cn.jobsearch.service.browser.BrowserAutomationService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class BossBrowserAgentTools extends AgentToolSupport {
    private final BrowserAutomationService browser;
    private final AgentConfirmationService confirmations;

    public BossBrowserAgentTools(BrowserAutomationService browser, AgentConfirmationService confirmations,
                                 AgentExecutionGuard guard, AgentRunRecorder recorder) {
        super(guard, recorder);
        this.browser = browser;
        this.confirmations = confirmations;
    }

    @Tool(name = "open_boss_for_manual_search", description = "为 BOSS 职位搜集打开或复用真实浏览器基础页，并创建人工确认单。只打开基础页，不自动填词、搜索、翻页或采集。调用后应停止其他工具并等待用户操作。")
    public BossOpenToolView openForManualSearch(
            @ToolParam(required = false, description = "卡片缺少城市时使用的默认城市") String city,
            @ToolParam(required = false, description = "后续确认采集的最多职位数，1到20，默认10") Integer maxItems,
            ToolContext context) {
        return execute(context, "open_boss_for_manual_search", () -> {
            BrowserStatusResponse status = browser.openBoss(true, false);
            AgentConfirmation confirmation = confirmations.requestBossCollection(runId(context), city, maxItems);
            BossOpenToolView view = new BossOpenToolView(status, confirmation.getId(),
                    "请用户手动登录和搜索，激活职位列表后在前端确认；模型不得代替用户确认");
            return new ToolOutcome<>(view, "BOSS 基础页已处理，确认单 " + confirmation.getId() + " 等待人工搜索");
        });
    }

    @Tool(name = "get_boss_browser_status", description = "读取当前 BOSS 浏览器活动标签状态，不刷新网页，不读取 Cookie。")
    public BrowserStatusResponse status(ToolContext context) {
        return execute(context, "get_boss_browser_status", () -> {
            BrowserStatusResponse status = browser.status();
            return new ToolOutcome<>(status, "已同步 BOSS 浏览器状态：" + status.currentUrl());
        });
    }

    public record BossOpenToolView(BrowserStatusResponse browser, Long confirmationId, String nextAction) {
    }
}
