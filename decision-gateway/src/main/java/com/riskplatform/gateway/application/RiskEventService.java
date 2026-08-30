package com.riskplatform.gateway.application;

import com.riskplatform.gateway.domain.AdoptionMode;
import com.riskplatform.gateway.domain.AgentStrategyPort;
import com.riskplatform.gateway.domain.AiAdviseOutcome;
import com.riskplatform.gateway.domain.AiDecisionMerger;
import com.riskplatform.gateway.domain.ContextFieldSupport;
import com.riskplatform.gateway.domain.EngineEvaluationResult;
import com.riskplatform.gateway.domain.EngineGateway;
import com.riskplatform.gateway.domain.EventIdGenerator;
import com.riskplatform.gateway.domain.InvokeMode;
import com.riskplatform.gateway.domain.ListGateway;
import com.riskplatform.gateway.domain.OrderStore;
import com.riskplatform.gateway.domain.RiskEventValidator;
import com.riskplatform.gateway.domain.ScreeningGateway;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import com.riskplatform.gateway.infrastructure.decisionlog.MySqlDecisionExecutionLogRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * 风控事件受理与编排应用服务（R2/R10/R16.1 + enhancement T1）。
 *
 * <p>流程：校验 → 精确名单 enrichment → 引擎决策 → 名单/筛查合并 →
 * 按采纳模式（SHADOW 异步 / 其余同步）处理 AI → 写回最终决策。
 */
public class RiskEventService {

    private final RiskEventValidator validator;
    private final EventIdGenerator eventIdGenerator;
    private final EngineGateway engineGateway;
    private final OrderStore orderStore;
    private final ListGateway listGateway;
    private final ScreeningGateway screeningGateway;
    private final DecisionExecutionLogService decisionExecutionLogService;
    private final AgentLlmProperties agentLlmProperties;
    private final AgentStrategyPort agentStrategyPort;

    public RiskEventService(RiskEventValidator validator,
                            EventIdGenerator eventIdGenerator,
                            EngineGateway engineGateway,
                            OrderStore orderStore,
                            ListGateway listGateway,
                            ScreeningGateway screeningGateway,
                            DecisionExecutionLogService decisionExecutionLogService) {
        this(validator, eventIdGenerator, engineGateway, orderStore, listGateway, screeningGateway,
                decisionExecutionLogService, null, null);
    }

    public RiskEventService(RiskEventValidator validator,
                            EventIdGenerator eventIdGenerator,
                            EngineGateway engineGateway,
                            OrderStore orderStore,
                            ListGateway listGateway,
                            ScreeningGateway screeningGateway,
                            DecisionExecutionLogService decisionExecutionLogService,
                            AgentLlmProperties agentLlmProperties) {
        this(validator, eventIdGenerator, engineGateway, orderStore, listGateway, screeningGateway,
                decisionExecutionLogService, agentLlmProperties, null);
    }

    public RiskEventService(RiskEventValidator validator,
                            EventIdGenerator eventIdGenerator,
                            EngineGateway engineGateway,
                            OrderStore orderStore,
                            ListGateway listGateway,
                            ScreeningGateway screeningGateway,
                            DecisionExecutionLogService decisionExecutionLogService,
                            AgentLlmProperties agentLlmProperties,
                            AgentStrategyPort agentStrategyPort) {
        this.validator = validator;
        this.eventIdGenerator = eventIdGenerator;
        this.engineGateway = engineGateway;
        this.orderStore = orderStore;
        this.listGateway = listGateway;
        this.screeningGateway = screeningGateway;
        this.decisionExecutionLogService = decisionExecutionLogService;
        this.agentLlmProperties = agentLlmProperties;
        this.agentStrategyPort = agentStrategyPort;
    }

    public RiskEventResult accept(String eventTypeCode,
                                  Map<String, Object> context,
                                  int contextSizeBytes) {
        return accept(eventTypeCode, context, contextSizeBytes, InvokeMode.AUTO, null, null);
    }

    public RiskEventResult accept(String eventTypeCode,
                                  Map<String, Object> context,
                                  int contextSizeBytes,
                                  InvokeMode invokeMode,
                                  Long rulePackageId,
                                  Long decisionFlowId) {
        validator.validate(eventTypeCode, context, contextSizeBytes);

        String eventId = eventIdGenerator.generate();
        String correlationId = MySqlDecisionExecutionLogRepository.correlationIdFromEventId(eventId);
        long eventTimeMs = System.currentTimeMillis();
        String merchantId = ContextFieldSupport.extractMerchantId(context);
        String businessOrderId = ContextFieldSupport.extractBusinessOrderId(context);

        orderStore.persistAsync(eventId, businessOrderId, eventTypeCode, context, eventTimeMs);

        Map<String, Object> enriched = listGateway.enrichContext(context);

        EngineEvaluationResult engineResult = engineGateway.evaluateDetailed(
                eventId, eventTypeCode, enriched, invokeMode, rulePackageId, decisionFlowId);

        String engineDecision = engineResult.decision();
        String engineTrack = finalizeDecision(engineDecision, enriched);

        AdoptionMode adoptionMode = resolveAdoptionMode(eventTypeCode);
        Map<String, Object> detail = new HashMap<>();
        if (engineResult.detail() != null) {
            detail.putAll(engineResult.detail());
        }
        detail.put("adoptionMode", adoptionMode.name());
        detail.put("engineDecision", engineDecision);
        detail.put("engineTrackDecision", engineTrack);

        String finalDecision;
        if (adoptionMode.requiresSyncAi()) {
            AiAdviseOutcome aiOutcome = decisionExecutionLogService.adviseSync(
                    eventId,
                    correlationId,
                    businessOrderId,
                    merchantId,
                    eventTypeCode,
                    eventTimeMs,
                    engineDecision,
                    enriched,
                    resolveAiSyncTimeoutMs());
            finalDecision = AiDecisionMerger.merge(adoptionMode, engineTrack, aiOutcome);
            detail.put("ai", toAiDetail(aiOutcome));
        } else {
            finalDecision = engineTrack;
            decisionExecutionLogService.scheduleAiAdvise(
                    eventId,
                    correlationId,
                    businessOrderId,
                    merchantId,
                    eventTypeCode,
                    eventTimeMs,
                    engineDecision,
                    enriched);
        }

        Long elapsedMs = extractElapsedMs(detail);
        decisionExecutionLogService.recordEngineDecision(
                eventId,
                correlationId,
                businessOrderId,
                merchantId,
                eventTypeCode,
                eventTimeMs,
                engineDecision,
                finalDecision,
                engineResult.invokeMode(),
                engineResult.rulePackageId(),
                engineResult.decisionFlowId(),
                detail,
                elapsedMs);

        orderStore.updateDecisionAsync(eventId, finalDecision);

        return new RiskEventResult(
                eventId,
                finalDecision,
                engineResult.invokeMode(),
                engineResult.rulePackageId(),
                engineResult.decisionFlowId(),
                detail);
    }

    /** 优先策略表 adoption_mode，其次网关默认配置。 */
    private AdoptionMode resolveAdoptionMode(String eventTypeCode) {
        if (agentStrategyPort != null) {
            try {
                AgentStrategyPort.ResolvedAgentStrategy strategy = agentStrategyPort.resolve(eventTypeCode);
                if (strategy != null && strategy.adoptionMode() != null && !strategy.adoptionMode().isBlank()) {
                    return AdoptionMode.from(strategy.adoptionMode());
                }
            } catch (Exception ignored) {
                // 回落默认
            }
        }
        if (agentLlmProperties == null || agentLlmProperties.getOrchestration() == null) {
            return AdoptionMode.SHADOW;
        }
        return AdoptionMode.from(agentLlmProperties.getOrchestration().getDefaultAdoptionMode());
    }

    private long resolveAiSyncTimeoutMs() {
        if (agentLlmProperties == null || agentLlmProperties.getOrchestration() == null) {
            return 8000L;
        }
        long ms = agentLlmProperties.getOrchestration().getAiSyncTimeoutMs();
        return ms > 0 ? ms : 8000L;
    }

    private static Map<String, Object> toAiDetail(AiAdviseOutcome outcome) {
        Map<String, Object> ai = new HashMap<>();
        ai.put("success", outcome.success());
        ai.put("timedOut", outcome.timedOut());
        if (outcome.failReason() != null) {
            ai.put("failReason", outcome.failReason());
        }
        if (outcome.result() != null) {
            ai.put("decision", outcome.result().decision());
            ai.put("confidence", outcome.result().confidence());
            ai.put("reason", outcome.result().reason());
        }
        return ai;
    }

    /** 合并精确名单、关注名单与名称模糊筛查结论（REJECT &gt; REVIEW &gt; PASS）。 */
    private String finalizeDecision(String engineDecision, Map<String, Object> context) {
        String decision = engineDecision;
        if (isTrue(context.get("blackHit"))) {
            decision = AiDecisionMerger.strictest(decision, "REJECT");
        }
        if (isTrue(context.get("watchHit"))) {
            decision = AiDecisionMerger.strictest(decision, "REVIEW");
        }
        String subjectName = extractSubjectName(context);
        ScreeningGateway.HitKind kind = screeningGateway.screenName(subjectName);
        if (kind == ScreeningGateway.HitKind.BLACK) {
            decision = AiDecisionMerger.strictest(decision, "REJECT");
        } else if (kind == ScreeningGateway.HitKind.WATCH) {
            decision = AiDecisionMerger.strictest(decision, "REVIEW");
        }
        return AiDecisionMerger.normalize(decision);
    }

    private static Long extractElapsedMs(Map<String, Object> detail) {
        if (detail == null) {
            return null;
        }
        Object raw = detail.get("elapsedMs");
        if (raw instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private static boolean isTrue(Object flag) {
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }

    private static String extractSubjectName(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        for (String key : new String[]{"subjectName", "payerName", "counterpartyName", "name"}) {
            Object v = context.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }
}
