package com.riskplatform.gateway.infrastructure.standalone;

import com.riskplatform.engine.application.DecisionFlowRuntimeLoader;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowEngine;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowResult;
import com.riskplatform.engine.domain.decisionflow.FlowTraceStep;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinition;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinitionPort;
import com.riskplatform.engine.domain.rulepackage.RulePackageExecutor;
import com.riskplatform.engine.domain.rulepackage.RulePackageResult;
import com.riskplatform.engine.domain.rule.HitDecision;
import com.riskplatform.engine.domain.rule.RuleExecutionRecord;
import com.riskplatform.engine.infrastructure.runtime.RuntimeBindingReadMapper;
import com.riskplatform.gateway.application.DecisionNormalizer;
import com.riskplatform.gateway.domain.EngineEvaluationResult;
import com.riskplatform.gateway.domain.EngineGateway;
import com.riskplatform.gateway.domain.InvokeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** standalone：内嵌 rule-decision-engine 执行逻辑，不依赖 8083 HTTP。 */
@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class EmbeddedEngineGateway implements EngineGateway {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedEngineGateway.class);

    private final RuntimeBindingReadMapper bindingMapper;
    private final RulePackageDefinitionPort rulePackageDefinitionPort;
    private final RulePackageExecutor rulePackageExecutor;
    private final DecisionFlowRuntimeLoader flowRuntimeLoader;
    private final DecisionFlowEngine decisionFlowEngine;

    public EmbeddedEngineGateway(RuntimeBindingReadMapper bindingMapper,
                                 RulePackageDefinitionPort rulePackageDefinitionPort,
                                 @Qualifier("onlineRulePackageExecutor") RulePackageExecutor rulePackageExecutor,
                                 DecisionFlowRuntimeLoader flowRuntimeLoader,
                                 DecisionFlowEngine decisionFlowEngine) {
        this.bindingMapper = bindingMapper;
        this.rulePackageDefinitionPort = rulePackageDefinitionPort;
        this.rulePackageExecutor = rulePackageExecutor;
        this.flowRuntimeLoader = flowRuntimeLoader;
        this.decisionFlowEngine = decisionFlowEngine;
    }

    @Override
    public EngineEvaluationResult evaluateDetailed(String eventId,
                                                   String eventTypeCode,
                                                   Map<String, Object> context,
                                                   InvokeMode invokeMode,
                                                   Long rulePackageId,
                                                   Long decisionFlowId) {
        Map<String, Object> ctx = context == null ? Map.of() : context;
        String eid = eventId == null ? "" : eventId;
        try {
            Map<String, Object> bindings = invokeMode == InvokeMode.AUTO
                    ? Map.of(
                    "rulePackageIds", bindingMapper.selectRulePackageIdsByEvent(eventTypeCode),
                    "decisionFlowId", bindingMapper.selectEnabledFlowIdByEvent(eventTypeCode))
                    : Map.of();
            InvokeMode resolved = resolveMode(invokeMode, bindings, rulePackageId, decisionFlowId);
            return switch (resolved) {
                case RULE_PACKAGE -> evaluateRulePackage(
                        eid, eventTypeCode, requirePackageId(rulePackageId, bindings), ctx);
                case DECISION_FLOW -> evaluateDecisionFlow(
                        eid, eventTypeCode, requireFlowId(decisionFlowId, bindings), ctx);
                case AUTO -> throw new IllegalStateException("AUTO must be resolved before evaluation");
            };
        } catch (Exception ex) {
            log.error("内嵌引擎评估失败，降级为 REVIEW: eventId={}, eventTypeCode={}, mode={}",
                    eid, eventTypeCode, invokeMode, ex);
            return EngineEvaluationResult.failClosed(invokeMode == null ? "AUTO" : invokeMode.name());
        }
    }

    private EngineEvaluationResult evaluateRulePackage(String eventId, String eventTypeCode,
                                                       long packageId, Map<String, Object> context) {
        RulePackageDefinition definition = rulePackageDefinitionPort.load(packageId);
        if (definition == null) {
            log.warn("规则包不存在，降级为 REVIEW: packageId={}", packageId);
            return EngineEvaluationResult.failClosed("RULE_PACKAGE");
        }
        RulePackageResult result = rulePackageExecutor.execute(definition, context);
        Map<String, Object> detail = new HashMap<>();
        detail.put("decision", result.decision().name());
        detail.put("triggerMode", result.triggerMode().name());
        if (result.score() != null) {
            detail.put("score", result.score());
        }
        if (result.riskLevelCode() != null) {
            detail.put("riskLevelCode", result.riskLevelCode());
        }
        detail.put("hits", toHitMaps(result.hitRules()));
        detail.put("records", toRecordMaps(result.executionRecords()));
        detail.put("groupStatus", result.groupStatus().name());
        detail.put("selectorMatch", selectorMatch("RULE_PACKAGE", eventTypeCode, packageId, null));
        return EngineEvaluationResult.rulePackage(
                DecisionNormalizer.toBusinessDecision(result.decision().name()),
                packageId,
                detail);
    }

    private EngineEvaluationResult evaluateDecisionFlow(String eventId, String eventTypeCode,
                                                        long flowId, Map<String, Object> context) {
        DecisionFlowRuntimeLoader.LoadedDecisionFlow loaded = flowRuntimeLoader.load(flowId);
        if (loaded == null) {
            log.warn("决策流不存在，降级为 REVIEW: flowId={}", flowId);
            return EngineEvaluationResult.failClosed("DECISION_FLOW");
        }
        DecisionFlowResult result = decisionFlowEngine.evaluate(loaded.definition(), context);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("decision", result.finalDecision());
        detail.put("hits", toHitMaps(result.hits()));
        detail.put("flowPath", result.path());
        detail.put("flowTrace", toFlowTraceMaps(result.trace()));
        detail.put("selectorMatch", selectorMatch("DECISION_FLOW", eventTypeCode, null, flowId));
        return EngineEvaluationResult.decisionFlow(
                DecisionNormalizer.toBusinessDecision(result.finalDecision()),
                flowId,
                detail);
    }

    private static Map<String, Object> selectorMatch(String invokeMode, String eventTypeCode,
                                                     Long rulePackageId, Long decisionFlowId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invokeMode", invokeMode);
        m.put("eventTypeCode", eventTypeCode);
        if (rulePackageId != null) {
            m.put("rulePackageId", rulePackageId);
        }
        if (decisionFlowId != null) {
            m.put("decisionFlowId", decisionFlowId);
        }
        return m;
    }

    private static List<Map<String, Object>> toHitMaps(List<HitDecision> hitRules) {
        if (hitRules == null || hitRules.isEmpty()) {
            return List.of();
        }
        return hitRules.stream().map(h -> {
            Map<String, Object> m = new HashMap<>();
            m.put("ruleId", h.ruleId());
            m.put("priority", h.priority());
            m.put("decision", h.decision().name());
            m.put("trialRun", h.trialRun());
            return m;
        }).toList();
    }

    private static List<Map<String, Object>> toRecordMaps(List<RuleExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (RuleExecutionRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleId", r.ruleId());
            m.put("version", r.version());
            m.put("hit", r.hit());
            m.put("failed", r.failed());
            if (r.failReason() != null) {
                m.put("failReason", r.failReason());
            }
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> toFlowTraceMaps(List<FlowTraceStep> trace) {
        if (trace == null || trace.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (FlowTraceStep step : trace) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", step.nodeId());
            m.put("nodeType", step.nodeType());
            m.put("refType", step.refType());
            m.put("refId", step.refId());
            m.put("hits", toHitMaps(step.hits()));
            m.put("assignments", step.assignments());
            out.add(m);
        }
        return out;
    }

    private InvokeMode resolveMode(InvokeMode mode,
                                   Map<String, Object> bindings,
                                   Long rulePackageId,
                                   Long decisionFlowId) {
        if (mode != InvokeMode.AUTO) {
            return mode;
        }
        if (decisionFlowId != null || asLong(bindings.get("decisionFlowId")) != null) {
            return InvokeMode.DECISION_FLOW;
        }
        if (rulePackageId != null || !asLongList(bindings.get("rulePackageIds")).isEmpty()) {
            return InvokeMode.RULE_PACKAGE;
        }
        throw new IllegalArgumentException("未配置决策流或规则包");
    }

    private long requirePackageId(Long explicitId, Map<String, Object> bindings) {
        if (explicitId != null) {
            return explicitId;
        }
        List<Long> ids = asLongList(bindings.get("rulePackageIds"));
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        throw new IllegalArgumentException("未找到可执行的规则包");
    }

    private long requireFlowId(Long explicitId, Map<String, Object> bindings) {
        if (explicitId != null) {
            return explicitId;
        }
        Long flowId = asLong(bindings.get("decisionFlowId"));
        if (flowId != null) {
            return flowId;
        }
        throw new IllegalArgumentException("未找到可执行的决策流");
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Long> asLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(EmbeddedEngineGateway::asLong).filter(java.util.Objects::nonNull).toList();
    }
}
