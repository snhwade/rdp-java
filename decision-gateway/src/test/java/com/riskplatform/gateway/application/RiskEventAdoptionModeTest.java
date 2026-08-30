package com.riskplatform.gateway.application;

import com.riskplatform.gateway.domain.AiAdviseResult;
import com.riskplatform.gateway.domain.EngineEvaluationResult;
import com.riskplatform.gateway.domain.EngineGateway;
import com.riskplatform.gateway.domain.InvokeMode;
import com.riskplatform.gateway.domain.EventIdGenerator;
import com.riskplatform.gateway.domain.ListGateway;
import com.riskplatform.gateway.domain.OrderStore;
import com.riskplatform.gateway.domain.RiskEventValidator;
import com.riskplatform.gateway.domain.ScreeningGateway;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1：采纳模式对同步对外决策的影响。
 */
class RiskEventAdoptionModeTest {

    private final EventIdGenerator idGen = () -> "evt-adopt";
    private static final ListGateway NO_LIST = context -> ListGateway.ListCheckSummary.empty();
    private static final ScreeningGateway NO_SCREEN = subjectName -> ScreeningGateway.HitKind.NONE;

    private RiskEventValidator enabledValidator() {
        return new RiskEventValidator(code -> RiskEventValidator.EventTypeStatusChecker.Status.ENABLED);
    }

    private DecisionExecutionLogService logWithAi(String aiDecision) {
        return new DecisionExecutionLogService(
                new RiskEventServiceTest.NoopDecisionLogStore(),
                (eventId, eventTypeCode, context, engineDecision) ->
                        new AiAdviseResult(aiDecision, 0.9, "unit-test", java.util.List.of()),
                Runnable::run);
    }

    private AgentLlmProperties props(String mode) {
        AgentLlmProperties p = new AgentLlmProperties();
        p.getOrchestration().setDefaultAdoptionMode(mode);
        p.getOrchestration().setAiSyncTimeoutMs(2000);
        return p;
    }

    private RiskEventService svc(String mode, String aiDecision) {
        EngineGateway engine = (eventId, eventTypeCode, context, invokeMode, rulePackageId, decisionFlowId) ->
                EngineEvaluationResult.rulePackage("PASS", 1L, Map.of());
        return new RiskEventService(
                enabledValidator(), idGen, engine, new NoopOrderStore(), NO_LIST, NO_SCREEN,
                logWithAi(aiDecision), props(mode));
    }

    @Test
    void shadow_keepsEngineDecision_evenIfAiWouldReject() {
        RiskEventResult r = svc("SHADOW", "REJECT").accept("B2B_RECV", Map.of("amount", 1), 10);
        assertThat(r.decision()).isEqualTo("PASS");
        assertThat(r.detail().get("adoptionMode")).isEqualTo("SHADOW");
    }

    @Test
    void advisory_aiReject_becomesReview() {
        RiskEventResult r = svc("ADVISORY", "REJECT").accept("B2B_RECV", Map.of("amount", 1), 10);
        assertThat(r.decision()).isEqualTo("REVIEW");
        @SuppressWarnings("unchecked")
        Map<String, Object> ai = (Map<String, Object>) r.detail().get("ai");
        assertThat(ai.get("decision")).isEqualTo("REJECT");
        assertThat(ai.get("success")).isEqualTo(true);
    }

    @Test
    void strict_aiReject_becomesReject() {
        RiskEventResult r = svc("STRICT", "REJECT").accept("B2B_RECV", Map.of("amount", 1), 10);
        assertThat(r.decision()).isEqualTo("REJECT");
    }

    @Test
    void override_usesAiDecision() {
        RiskEventResult r = svc("OVERRIDE", "REVIEW").accept("B2B_RECV", Map.of("amount", 1), 10);
        assertThat(r.decision()).isEqualTo("REVIEW");
    }

    @Test
    void shadow_schedulesAsync_withoutBlockingOnAi() {
        AtomicInteger aiCalls = new AtomicInteger();
        DecisionExecutionLogService log = new DecisionExecutionLogService(
                new RiskEventServiceTest.NoopDecisionLogStore(),
                (eventId, eventTypeCode, context, engineDecision) -> {
                    aiCalls.incrementAndGet();
                    return new AiAdviseResult("REJECT", 1.0, "async", java.util.List.of());
                },
                Runnable::run);
        EngineGateway engine = (eventId, eventTypeCode, context, invokeMode, rulePackageId, decisionFlowId) ->
                EngineEvaluationResult.rulePackage("PASS", 1L, Map.of());
        RiskEventService service = new RiskEventService(
                enabledValidator(), idGen, engine, new NoopOrderStore(), NO_LIST, NO_SCREEN,
                log, props("SHADOW"));
        RiskEventResult r = service.accept("B2B_RECV", Map.of("amount", 1), 10);
        assertThat(r.decision()).isEqualTo("PASS");
        assertThat(aiCalls.get()).isEqualTo(1);
        assertThat(r.detail().get("ai")).isNull();
    }

    static class NoopOrderStore implements OrderStore {
        @Override
        public void persistAsync(String eventId, String businessOrderId, String code, Map<String, Object> ctx, long t) {
        }

        @Override
        public void updateDecisionAsync(String eventId, String decision) {
        }
    }
}
