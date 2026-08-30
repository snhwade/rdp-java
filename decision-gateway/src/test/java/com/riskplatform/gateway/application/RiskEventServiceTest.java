package com.riskplatform.gateway.application;

import com.riskplatform.common.error.BizException;
import com.riskplatform.gateway.domain.EngineEvaluationResult;
import com.riskplatform.gateway.domain.EngineGateway;
import com.riskplatform.gateway.domain.InvokeMode;
import com.riskplatform.gateway.domain.EventIdGenerator;
import com.riskplatform.gateway.domain.ListGateway;
import com.riskplatform.gateway.domain.OrderStore;
import com.riskplatform.gateway.domain.RiskEventValidator;
import com.riskplatform.gateway.domain.ScreeningGateway;
import com.riskplatform.gateway.application.DecisionExecutionLogService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 决策网关受理与编排单元测试（R2/R10/R11/S1）。
 */
class RiskEventServiceTest {

    private RiskEventValidator validatorWith(RiskEventValidator.EventTypeStatusChecker.Status status) {
        return new RiskEventValidator(code -> status);
    }

    private final EventIdGenerator idGen = () -> "evt-fixed";

    private static final ListGateway NO_LIST = context -> ListGateway.ListCheckSummary.empty();

    private static final ScreeningGateway NO_SCREEN = subjectName -> ScreeningGateway.HitKind.NONE;

    private static final DecisionExecutionLogService NO_DECISION_LOG = new DecisionExecutionLogService(
            new NoopDecisionLogStore(),
            (eventId, eventTypeCode, context, engineDecision) ->
                    new com.riskplatform.gateway.domain.AiAdviseResult("PASS", 0, "", java.util.List.of()),
            Runnable::run);

    static class NoopDecisionLogStore implements com.riskplatform.gateway.domain.DecisionExecutionLogStore {
        @Override
        public void saveEngineRecord(String eventId, String correlationId, String businessOrderId, String merchantId,
                                     String eventTypeCode, long eventTimeMs, String engineDecision,
                                     String finalDecision, String invokeMode, Long rulePackageId,
                                     Long decisionFlowId, java.util.Map<String, Object> detail, Long elapsedMs) {
        }

        @Override
        public void createAiPending(String eventId, String correlationId, String businessOrderId, String merchantId,
                                    String eventTypeCode, long eventTimeMs, String engineDecision) {
        }

        @Override
        public void completeAiSuccess(String eventId,
                                      com.riskplatform.gateway.domain.AiAdviseResult result,
                                      String engineDecision) {
        }

        @Override
        public void completeAiFailed(String eventId, String failReason) {
        }

        @Override
        public com.riskplatform.common.model.PagedResult<com.riskplatform.gateway.domain.EngineDecisionRecordView> queryEngine(
                com.riskplatform.gateway.domain.DecisionRecordQuery query) {
            return com.riskplatform.common.model.PagedResult.empty(1, 20);
        }

        @Override
        public com.riskplatform.common.model.PagedResult<com.riskplatform.gateway.domain.AiDecisionRecordView> queryAi(
                com.riskplatform.gateway.domain.DecisionRecordQuery query) {
            return com.riskplatform.common.model.PagedResult.empty(1, 20);
        }

        @Override
        public com.riskplatform.gateway.domain.EngineDecisionRecordView findEngineByEventId(String eventId) {
            return null;
        }

        @Override
        public com.riskplatform.gateway.domain.AiDecisionRecordView findAiByEventId(String eventId) {
            return null;
        }

        @Override
        public java.util.Map<String, com.riskplatform.gateway.domain.AiDecisionRecordView> findAiByEventIds(
                java.util.Collection<String> eventIds) {
            return java.util.Map.of();
        }

        @Override
        public com.riskplatform.gateway.domain.AiDecisionStatsView queryAiStats(
                Long startTimeMs, Long endTimeMs, String eventTypeCode) {
            return new com.riskplatform.gateway.domain.AiDecisionStatsView(
                    0, 0, 0, 0, 0, 0, 0.0, 0.0, java.util.List.of(), java.util.List.of(), 0, 0, 0.0);
        }

        @Override
        public com.riskplatform.gateway.domain.EngineDecisionStatsView queryEngineStats(
                Long startTimeMs, Long endTimeMs, String eventTypeCode) {
            return new com.riskplatform.gateway.domain.EngineDecisionStatsView(
                    0, java.util.Map.of(), 0.0, 0L, java.util.List.of());
        }
    }

    static class CountingEngine implements EngineGateway {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<Map<String, Object>> lastContext = new AtomicReference<>();

        @Override
        public EngineEvaluationResult evaluateDetailed(String eventId,
                                                       String eventTypeCode,
                                                       Map<String, Object> context,
                                                       InvokeMode invokeMode,
                                                       Long rulePackageId,
                                                       Long decisionFlowId) {
            calls.incrementAndGet();
            lastContext.set(context);
            return EngineEvaluationResult.rulePackage("PASS", 1L, Map.of());
        }
    }

    static class RecordingOrderStore implements OrderStore {
        int persistCalls = 0;
        int decisionCalls = 0;

        @Override
        public void persistAsync(String eventId, String businessOrderId, String code, Map<String, Object> ctx, long t) {
            persistCalls++;
        }

        @Override
        public void updateDecisionAsync(String eventId, String decision) {
            decisionCalls++;
        }
    }

    private RiskEventService service(EngineGateway engine,
                                     ListGateway listGateway,
                                     ScreeningGateway screening) {
        return new RiskEventService(
                validatorWith(RiskEventValidator.EventTypeStatusChecker.Status.ENABLED),
                idGen, engine, new RecordingOrderStore(), listGateway, screening, NO_DECISION_LOG);
    }

    @Test
    void accept_enabledType_returnsDecision_andTriggersOnce() {
        CountingEngine engine = new CountingEngine();
        RecordingOrderStore store = new RecordingOrderStore();
        RiskEventService svc = new RiskEventService(
                validatorWith(RiskEventValidator.EventTypeStatusChecker.Status.ENABLED),
                idGen, engine, store, NO_LIST, NO_SCREEN, NO_DECISION_LOG);

        RiskEventResult result = svc.accept("B2B_RECV", Map.of("amount", 100), 50);

        assertThat(result.eventId()).isEqualTo("evt-fixed");
        assertThat(result.decision()).isEqualTo("PASS");
        assertThat(engine.calls.get()).isEqualTo(1);
        assertThat(store.persistCalls).isEqualTo(1);
        assertThat(store.decisionCalls).isEqualTo(1);
    }

    @Test
    void accept_exactBlackHit_injectsContext_andRejects() {
        CountingEngine engine = new CountingEngine();
        ListGateway blackList = ctx -> new ListGateway.ListCheckSummary(true, false, false, false, java.util.List.of());
        RiskEventResult result = service(engine, blackList, NO_SCREEN)
                .accept("B2B_RECV", Map.of("merchantId", "M123"), 50);

        assertThat(result.decision()).isEqualTo("REJECT");
        assertThat(engine.lastContext.get().get("blackHit")).isEqualTo(true);
    }

    @Test
    void accept_fuzzyWatchHit_reviewsNotReject() {
        ScreeningGateway watch = name -> ScreeningGateway.HitKind.WATCH;
        RiskEventResult result = service(new CountingEngine(), NO_LIST, watch)
                .accept("B2B_RECV", Map.of("subjectName", "Acme"), 50);

        assertThat(result.decision()).isEqualTo("REVIEW");
    }

    @Test
    void accept_fuzzyBlackHit_rejects() {
        ScreeningGateway black = name -> ScreeningGateway.HitKind.BLACK;
        RiskEventResult result = service(new CountingEngine(), NO_LIST, black)
                .accept("B2B_RECV", Map.of("subjectName", "Bad Guy"), 50);

        assertThat(result.decision()).isEqualTo("REJECT");
    }

    @Test
    void accept_missingFields_rejected_noTrigger() {
        CountingEngine engine = new CountingEngine();
        RiskEventService svc = service(engine, NO_LIST, NO_SCREEN);
        assertThatThrownBy(() -> svc.accept("", Map.of(), 0)).isInstanceOf(Exception.class);
        assertThat(engine.calls.get()).isZero();
    }

    @Test
    void accept_disabledType_rejected() {
        RiskEventService svc = new RiskEventService(
                validatorWith(RiskEventValidator.EventTypeStatusChecker.Status.DISABLED),
                idGen, new CountingEngine(), new RecordingOrderStore(), NO_LIST, NO_SCREEN, NO_DECISION_LOG);
        assertThatThrownBy(() -> svc.accept("X", Map.of("a", 1), 10)).isInstanceOf(BizException.class);
    }
}
