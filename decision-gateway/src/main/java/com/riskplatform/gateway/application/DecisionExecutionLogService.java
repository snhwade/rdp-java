package com.riskplatform.gateway.application;



import com.riskplatform.common.error.BizException;

import com.riskplatform.common.error.CommonErrorCode;

import com.riskplatform.common.model.PagedResult;

import com.riskplatform.gateway.domain.AiAdviseOutcome;
import com.riskplatform.gateway.domain.AiAdviseResult;
import com.riskplatform.gateway.domain.AiAgentPort;
import com.riskplatform.gateway.domain.AiDecisionRecordView;
import com.riskplatform.gateway.domain.AiDecisionStatsView;
import com.riskplatform.gateway.domain.DecisionExecutionLogStore;
import com.riskplatform.gateway.domain.DecisionRecordQuery;
import com.riskplatform.gateway.domain.EngineDecisionRecordView;
import com.riskplatform.gateway.domain.InvocationDetailView;
import com.riskplatform.gateway.domain.UnifiedDecisionRecordView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;



import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



/**

 * 决策执行记录查询与 AI Agent 异步推理编排。

 */

public class DecisionExecutionLogService {



    private static final Logger log = LoggerFactory.getLogger(DecisionExecutionLogService.class);



    private final DecisionExecutionLogStore store;
    private final AiAgentPort aiAgentPort;
    private final Executor aiExecutor;
    private final com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics metrics;

    public DecisionExecutionLogService(
            DecisionExecutionLogStore store,
            AiAgentPort aiAgentPort,
            Executor aiExecutor) {
        this(store, aiAgentPort, aiExecutor, null);
    }

    public DecisionExecutionLogService(
            DecisionExecutionLogStore store,
            AiAgentPort aiAgentPort,
            Executor aiExecutor,
            com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics metrics) {
        this.store = store;
        this.aiAgentPort = aiAgentPort;
        this.aiExecutor = aiExecutor;
        this.metrics = metrics;
    }



    public void recordEngineDecision(

            String eventId,

            String correlationId,

            String businessOrderId,

            String merchantId,

            String eventTypeCode,

            long eventTimeMs,

            String engineDecision,

            String finalDecision,

            String invokeMode,

            Long rulePackageId,

            Long decisionFlowId,

            Map<String, Object> detail,

            Long elapsedMs) {

        store.saveEngineRecord(

                eventId, correlationId, businessOrderId, merchantId, eventTypeCode, eventTimeMs,

                engineDecision, finalDecision, invokeMode, rulePackageId, decisionFlowId,

                detail, elapsedMs);

    }



    public void scheduleAiAdvise(

            String eventId,

            String correlationId,

            String businessOrderId,

            String merchantId,

            String eventTypeCode,

            long eventTimeMs,

            String engineDecision,

            Map<String, Object> context) {

        store.createAiPending(

                eventId, correlationId, businessOrderId, merchantId, eventTypeCode, eventTimeMs, engineDecision);

        aiExecutor.execute(() -> runAiAdvise(eventId, eventTypeCode, context, engineDecision));
    }

    /**
     * 同步等待 AI 推理（ADVISORY / STRICT / OVERRIDE），写入 ai_decision_record。
     */
    public AiAdviseOutcome adviseSync(
            String eventId,
            String correlationId,
            String businessOrderId,
            String merchantId,
            String eventTypeCode,
            long eventTimeMs,
            String engineDecision,
            Map<String, Object> context,
            long timeoutMs) {
        store.createAiPending(
                eventId, correlationId, businessOrderId, merchantId, eventTypeCode, eventTimeMs, engineDecision);

        long waitMs = timeoutMs > 0 ? timeoutMs : 8000;
        Future<AiAdviseResult> future = null;
        try {
            if (aiExecutor instanceof ExecutorService es) {
                future = es.submit(() -> aiAgentPort.advise(eventId, eventTypeCode, context, engineDecision));
            } else {
                future = java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> aiAgentPort.advise(eventId, eventTypeCode, context, engineDecision),
                        aiExecutor);
            }
            AiAdviseResult result = future.get(waitMs, TimeUnit.MILLISECONDS);
            if (result == null) {
                store.completeAiFailed(eventId, "AI 返回空结果");
                markFail();
                return AiAdviseOutcome.failed("AI 返回空结果");
            }
            store.completeAiSuccess(eventId, result, engineDecision);
            markSuccess();
            return AiAdviseOutcome.ok(result);
        } catch (TimeoutException ex) {
            if (future != null) {
                future.cancel(true);
            }
            String reason = "AI sync timeout after " + waitMs + "ms";
            log.warn("AI Agent 同步推理超时: eventId={} {}", eventId, reason);
            store.completeAiFailed(eventId, reason);
            markFail();
            return AiAdviseOutcome.timedOut(reason);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            log.warn("AI Agent 同步推理失败: eventId={} 原因={}", eventId, reason);
            store.completeAiFailed(eventId, reason);
            markFail();
            return AiAdviseOutcome.failed(reason);
        }
    }

    private void runAiAdvise(
            String eventId,
            String eventTypeCode,
            Map<String, Object> context,
            String engineDecision) {
        try {
            var result = aiAgentPort.advise(eventId, eventTypeCode, context, engineDecision);
            store.completeAiSuccess(eventId, result, engineDecision);
            markSuccess();
        } catch (Exception ex) {
            log.warn("AI Agent 异步推理失败: eventId={} 原因={}", eventId, ex.getMessage());
            store.completeAiFailed(eventId, ex.getMessage());
            markFail();
        }
    }

    private void markSuccess() {
        if (metrics != null) {
            metrics.success();
        }
    }

    private void markFail() {
        if (metrics != null) {
            metrics.fail();
        }
    }



    public PagedResult<EngineDecisionRecordView> queryEngine(DecisionRecordQuery query) {

        return store.queryEngine(query);

    }



    public PagedResult<AiDecisionRecordView> queryAi(DecisionRecordQuery query) {

        return store.queryAi(query);

    }



    public EngineDecisionRecordView getEngine(String eventId) {

        return store.findEngineByEventId(eventId);

    }



    public AiDecisionRecordView getAi(String eventId) {
        return store.findAiByEventId(eventId);
    }

    public AiDecisionStatsView queryAiStats(Long startTimeMs, Long endTimeMs, String eventTypeCode) {
        return store.queryAiStats(startTimeMs, endTimeMs, eventTypeCode);
    }

    public InvocationDetailView getInvocationDetail(String eventId) {

        EngineDecisionRecordView engine = store.findEngineByEventId(eventId);

        AiDecisionRecordView ai = store.findAiByEventId(eventId);

        if (engine == null && ai == null) {

            throw new BizException(CommonErrorCode.NOT_FOUND, "未找到调用记录: " + eventId);

        }

        String businessOrderId = engine != null ? engine.businessOrderId()

                : (ai != null ? eventId : eventId);

        String correlationId = engine != null ? engine.correlationId()

                : (ai != null ? ai.correlationId() : "");

        String merchantId = engine != null ? engine.merchantId()

                : (ai != null ? ai.merchantId() : null);

        String eventTypeCode = engine != null ? engine.eventTypeCode()

                : (ai != null ? ai.eventTypeCode() : "");

        long eventTimeMs = engine != null ? engine.eventTimeMs()

                : (ai != null ? ai.eventTimeMs() : 0L);

        List<Map<String, Object>> hits = engine != null ? extractEngineHits(engine.detail()) : List.of();

        return new InvocationDetailView(

                eventId, businessOrderId, correlationId, merchantId, eventTypeCode,

                eventTimeMs, engine, ai, hits);

    }



    public PagedResult<UnifiedDecisionRecordView> queryDecisionRecords(DecisionRecordQuery query) {
        if (Boolean.TRUE.equals(query.divergenceOnly())) {
            PagedResult<AiDecisionRecordView> aiPage = store.queryAi(query);
            List<UnifiedDecisionRecordView> rows = new ArrayList<>();
            for (AiDecisionRecordView ai : aiPage.data()) {
                EngineDecisionRecordView engine = store.findEngineByEventId(ai.eventId());
                if (engine != null) {
                    rows.add(toUnifiedView(engine, ai));
                } else {
                    rows.add(toUnifiedFromAiOnly(ai));
                }
            }
            return PagedResult.of(rows, aiPage.page(), aiPage.pageSize(), aiPage.total());
        }

        PagedResult<EngineDecisionRecordView> enginePage = store.queryEngine(query);
        List<String> eventIds = enginePage.data().stream().map(EngineDecisionRecordView::eventId).toList();
        Map<String, AiDecisionRecordView> aiByEventId = store.findAiByEventIds(eventIds);
        List<UnifiedDecisionRecordView> rows = enginePage.data().stream()
                .map(engine -> toUnifiedView(engine, aiByEventId.get(engine.eventId())))
                .toList();
        return PagedResult.of(rows, enginePage.page(), enginePage.pageSize(), enginePage.total());
    }

    private static UnifiedDecisionRecordView toUnifiedFromAiOnly(AiDecisionRecordView ai) {
        return new UnifiedDecisionRecordView(
                ai.eventId(),
                ai.eventId(),
                ai.correlationId(),
                ai.merchantId(),
                ai.eventTypeCode() == null ? "" : ai.eventTypeCode(),
                ai.eventTimeMs(),
                ai.engineDecision() == null ? "" : ai.engineDecision(),
                ai.engineDecision() == null ? "" : ai.engineDecision(),
                null,
                null,
                null,
                null,
                ai.status(),
                ai.agentDecision(),
                ai.confidence(),
                ai.divergence(),
                ai.completedAtMs());
    }

    private static UnifiedDecisionRecordView toUnifiedView(

            EngineDecisionRecordView engine,

            AiDecisionRecordView ai) {

        return new UnifiedDecisionRecordView(

                engine.eventId(),

                engine.businessOrderId(),

                engine.correlationId(),

                engine.merchantId(),

                engine.eventTypeCode(),

                engine.eventTimeMs(),

                engine.engineDecision(),

                engine.finalDecision(),

                engine.invokeMode(),

                engine.rulePackageId(),

                engine.decisionFlowId(),

                engine.elapsedMs(),

                ai == null ? null : ai.status(),

                ai == null ? null : ai.agentDecision(),

                ai == null ? null : ai.confidence(),

                ai == null ? null : ai.divergence(),

                ai == null ? null : ai.completedAtMs());

    }



    @SuppressWarnings("unchecked")

    private static List<Map<String, Object>> extractEngineHits(Map<String, Object> detail) {

        if (detail == null || detail.isEmpty()) {

            return List.of();

        }

        Object raw = detail.get("hits");

        if (raw == null) {

            raw = detail.get("hitRules");

        }

        if (!(raw instanceof List<?> list)) {

            return List.of();

        }

        List<Map<String, Object>> hits = new ArrayList<>();

        for (Object item : list) {

            if (item instanceof Map<?, ?> map) {

                hits.add((Map<String, Object>) map);

            }

        }

        return hits;

    }

}


