package cn.jobsearch.service.agent;

import cn.jobsearch.config.AgentProperties;
import cn.jobsearch.domain.agent.AgentConfirmation;
import cn.jobsearch.domain.agent.AgentRun;
import cn.jobsearch.domain.agent.AgentRunStatus;
import cn.jobsearch.domain.agent.ConfirmationStatus;
import cn.jobsearch.repository.agent.AgentConfirmationRepository;
import cn.jobsearch.repository.agent.AgentRunRepository;
import cn.jobsearch.service.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AgentConfirmationService {
    public static final String BOSS_COLLECT = "BOSS_COLLECT_CURRENT_PAGE";

    private final AgentConfirmationRepository confirmations;
    private final AgentRunRepository runs;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public AgentConfirmationService(AgentConfirmationRepository confirmations, AgentRunRepository runs,
                                    AgentProperties properties, ObjectMapper objectMapper) {
        this.confirmations = confirmations;
        this.runs = runs;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentConfirmation requestBossCollection(Long runId, String city, Integer maxItems) {
        AgentRun run = getRun(runId);
        if (run.getPendingConfirmationId() != null) {
            return confirmations.findById(run.getPendingConfirmationId()).orElseThrow();
        }
        if (run.getStatus() != AgentRunStatus.RUNNING) {
            throw ApiException.badRequest("AGENT_NOT_RUNNING", "当前任务不处于可创建采集确认的状态");
        }
        BossCollectionPayload payload = new BossCollectionPayload(city == null ? "" : city.trim(),
                maxItems == null ? 10 : Math.max(1, Math.min(20, maxItems)));
        try {
            AgentConfirmation confirmation = confirmations.save(AgentConfirmation.pending(runId, BOSS_COLLECT,
                    objectMapper.writeValueAsString(payload),
                    LocalDateTime.now().plusMinutes(properties.getConfirmationTtlMinutes())));
            run.setPendingConfirmation(BOSS_COLLECT, confirmation.getId());
            run.setStatus(AgentRunStatus.WAITING_USER_ACTION);
            run.setResponse("BOSS 基础页已处理。请在真实浏览器中手动登录和搜索，激活职位列表标签后，回到本页点击确认采集。");
            runs.save(run);
            return confirmation;
        } catch (Exception ex) {
            throw ApiException.badRequest("CONFIRMATION_CREATE_FAILED", "无法创建当前页采集确认单");
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AgentConfirmation resolve(Long runId, Long confirmationId, boolean approved, String expectedAction) {
        AgentConfirmation confirmation = confirmations.findById(confirmationId)
                .orElseThrow(() -> ApiException.notFound("确认单不存在"));
        AgentRun run = getRun(runId);
        if (!confirmation.getRunId().equals(runId) || !confirmation.getAction().equals(expectedAction)
                || !confirmationId.equals(run.getPendingConfirmationId())) {
            throw ApiException.badRequest("CONFIRMATION_MISMATCH", "确认单与当前任务或动作不匹配");
        }
        if (confirmation.getStatus() != ConfirmationStatus.PENDING) {
            throw ApiException.badRequest("CONFIRMATION_ALREADY_RESOLVED", "确认单已经处理，不能重复使用");
        }
        if (confirmation.getExpiresAt().isBefore(LocalDateTime.now())) {
            confirmation.setStatus(ConfirmationStatus.EXPIRED);
            confirmations.save(confirmation);
            run.clearPendingConfirmation();
            run.setStatus(AgentRunStatus.FAILED);
            run.setResponse("人工确认已过期，请重新发起任务。");
            runs.save(run);
            throw ApiException.badRequest("CONFIRMATION_EXPIRED", "人工确认已过期，请重新发起任务");
        }
        confirmation.setStatus(approved ? ConfirmationStatus.APPROVED : ConfirmationStatus.REJECTED);
        confirmations.save(confirmation);
        run.clearPendingConfirmation();
        if (!approved) {
            run.setStatus(AgentRunStatus.CANCELLED);
            run.setResponse("你已取消本次人工确认动作，未采集当前页。");
            runs.save(run);
        }
        return confirmation;
    }

    @Transactional(readOnly = true)
    public BossCollectionPayload readBossPayload(AgentConfirmation confirmation) {
        try {
            return objectMapper.readValue(confirmation.getPayload(), BossCollectionPayload.class);
        } catch (Exception ex) {
            throw ApiException.badRequest("CONFIRMATION_PAYLOAD_INVALID", "确认单内容无法读取");
        }
    }

    private AgentRun getRun(Long runId) {
        return runs.findById(runId).orElseThrow(() -> ApiException.notFound("Agent 任务不存在：" + runId));
    }

    public record BossCollectionPayload(String city, Integer maxItems) {
    }
}
