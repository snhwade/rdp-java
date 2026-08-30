package com.riskplatform.engine.application;

import com.riskplatform.common.error.BizException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 执行链路查询服务单元测试（R15.3/R15.4）。
 */
class TraceQueryServiceTest {

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

    private DecisionLog sampleLog() {
        return new DecisionLog(
                "evt-1",
                Decision.REVIEW,
                List.of(new HitDecision(1L, 5, Decision.REVIEW), new HitDecision(2L, 8, Decision.PASS)),
                123L,
                null,
                GroupExecutionStatus.COMPLETED);
    }

    @Test
    void query_returnsFullChain_withTraceIdFallbackToEventId() {
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(sampleLog());
        TraceQueryService service = new TraceQueryService(repo);

        TraceView view = service.query("evt-1");

        assertThat(view.eventId()).isEqualTo("evt-1");
        assertThat(view.traceId()).isEqualTo("evt-1"); // 未启用分布式追踪时回退 eventId
        assertThat(view.finalDecision()).isEqualTo(Decision.REVIEW);
        assertThat(view.hitDecisions()).hasSize(2);
        assertThat(view.elapsedMs()).isEqualTo(123L);
        assertThat(view.groupStatus()).isEqualTo(GroupExecutionStatus.COMPLETED);
    }

    @Test
    void query_usesResolvedTraceId_whenProvided() {
        InMemoryRepo repo = new InMemoryRepo();
        repo.save(sampleLog());
        TraceQueryService service = new TraceQueryService(repo, eventId -> "trace-" + eventId);

        TraceView view = service.query("evt-1");
        assertThat(view.traceId()).isEqualTo("trace-evt-1");
    }

    @Test
    void query_missing_throwsNotFound() {
        TraceQueryService service = new TraceQueryService(new InMemoryRepo());
        assertThatThrownBy(() -> service.query("nope"))
                .isInstanceOf(BizException.class);
    }
}
