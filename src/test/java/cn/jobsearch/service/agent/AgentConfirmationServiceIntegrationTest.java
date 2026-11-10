package cn.jobsearch.service.agent;

import cn.jobsearch.domain.agent.AgentConfirmation;
import cn.jobsearch.domain.agent.AgentRun;
import cn.jobsearch.domain.agent.AgentRunStatus;
import cn.jobsearch.domain.agent.ConfirmationStatus;
import cn.jobsearch.repository.agent.AgentConfirmationRepository;
import cn.jobsearch.repository.agent.AgentRunRepository;
import cn.jobsearch.service.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-confirmation-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.demo.seed-enabled=false",
        "app.ai.api-key="
})
class AgentConfirmationServiceIntegrationTest {
    @Autowired AgentConfirmationService service;
    @Autowired AgentRunRepository runs;
    @Autowired AgentConfirmationRepository confirmations;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rejectsMismatchedConfirmationAndPreventsReuse() {
        AgentRun first = newRun();
        AgentRun second = newRun();
        AgentConfirmation confirmation = service.requestBossCollection(first.getId(), "杭州", 10);

        assertThatThrownBy(() -> service.resolve(second.getId(), confirmation.getId(), true,
                AgentConfirmationService.BOSS_COLLECT))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CONFIRMATION_MISMATCH"));

        service.resolve(first.getId(), confirmation.getId(), false, AgentConfirmationService.BOSS_COLLECT);
        assertThat(confirmations.findById(confirmation.getId()).orElseThrow().getStatus())
                .isEqualTo(ConfirmationStatus.REJECTED);
        assertThat(runs.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(AgentRunStatus.CANCELLED);
        assertThatThrownBy(() -> service.resolve(first.getId(), confirmation.getId(), false,
                AgentConfirmationService.BOSS_COLLECT))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void expiredConfirmationIsPersistedAndFailsTheRun() {
        AgentRun run = newRun();
        AgentConfirmation confirmation = service.requestBossCollection(run.getId(), "杭州", 20);
        jdbc.update("update agent_confirmation set expires_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), confirmation.getId());

        assertThatThrownBy(() -> service.resolve(run.getId(), confirmation.getId(), true,
                AgentConfirmationService.BOSS_COLLECT))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CONFIRMATION_EXPIRED"));

        assertThat(confirmations.findById(confirmation.getId()).orElseThrow().getStatus())
                .isEqualTo(ConfirmationStatus.EXPIRED);
        AgentRun saved = runs.findById(run.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(saved.getPendingConfirmationId()).isNull();
    }

    @Test
    void approvalClampsCollectionLimitAndConsumesConfirmation() {
        AgentRun run = newRun();
        AgentConfirmation confirmation = service.requestBossCollection(run.getId(), " 杭州 ", 99);

        AgentConfirmationService.BossCollectionPayload payload = service.readBossPayload(confirmation);
        assertThat(payload.city()).isEqualTo("杭州");
        assertThat(payload.maxItems()).isEqualTo(20);

        service.resolve(run.getId(), confirmation.getId(), true, AgentConfirmationService.BOSS_COLLECT);
        assertThat(confirmations.findById(confirmation.getId()).orElseThrow().getStatus())
                .isEqualTo(ConfirmationStatus.APPROVED);
        assertThat(runs.findById(run.getId()).orElseThrow().getPendingConfirmationId()).isNull();
        assertThatThrownBy(() -> service.resolve(run.getId(), confirmation.getId(), true,
                AgentConfirmationService.BOSS_COLLECT))
                .isInstanceOf(ApiException.class);
    }

    private AgentRun newRun() {
        return runs.save(AgentRun.create(UUID.randomUUID().toString(), "测试 BOSS 确认", "DEEPSEEK"));
    }
}
