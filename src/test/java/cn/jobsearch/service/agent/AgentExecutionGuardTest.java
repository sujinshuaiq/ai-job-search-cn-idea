package cn.jobsearch.service.agent;

import cn.jobsearch.config.AgentProperties;
import cn.jobsearch.service.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentExecutionGuardTest {

    @Test
    void enforcesPerRunBudgetAndClearReleasesIt() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxToolCalls(2);
        AgentExecutionGuard guard = new AgentExecutionGuard(properties);

        guard.beforeTool(1L);
        guard.beforeTool(1L);
        assertThatThrownBy(() -> guard.beforeTool(1L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getCode())
                                .isEqualTo("AGENT_TOOL_BUDGET_EXCEEDED"));
        assertThatCode(() -> guard.beforeTool(2L)).doesNotThrowAnyException();

        guard.clear(1L);
        assertThatCode(() -> guard.beforeTool(1L)).doesNotThrowAnyException();
    }
}
