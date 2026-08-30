package com.riskplatform.engine.application;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decision.DecisionLog;
import com.riskplatform.engine.domain.decision.DecisionLogRepository;
import com.riskplatform.engine.domain.rule.GroupExecutionStatus;
import com.riskplatform.engine.domain.rule.HitDecision;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 决策日志记录与查询单元测试（R6.6/R15.1/R15.3）。
 */
class DecisionLogServiceTest {

    /** 内存实现，模拟 decision_log 持久化。 */
    static class InMemoryRepo implements DecisionLogRepository {
        private final Map<String, DecisionLog> store = new HashMap<>();

        @Override
        public void save(DecisionLog log) {
            store.put(log.eventId(), log);
        }

        @Override
        public Optional<DecisionLog> findByEventId(String eventId) {
            return Optional.ofNullable(store.get(eventId));
        }
    }

    @Test
    void recordAndQuery_roundTrip() {
        DecisionLogService service = new DecisionLogService(new InMemoryRepo());
        DecisionLog log = new DecisionLog(
                "evt-1",
                Decision.REJECT,
                List.of(new HitDecision(1L, 10, Decision.REJECT)),
                42L,
                null,
                GroupExecutionStatus.COMPLETED);
        service.record(log);

        Optional<DecisionLog> found = service.query("evt-1");
        assertThat(found).isPresent();
        assertThat(found.get().finalDecision()).isEqualTo(Decision.REJECT);
        assertThat(found.get().hitDecisions()).hasSize(1);
        assertThat(found.get().elapsedMs()).isEqualTo(42L);
    }

    @Test
    void query_missing_returnsEmpty() {
        DecisionLogService service = new DecisionLogService(new InMemoryRepo());
        assertThat(service.query("nope")).isEmpty();
    }
}
