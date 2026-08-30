package com.riskplatform.gateway.infrastructure.client;

import com.riskplatform.gateway.application.DecisionNormalizer;
import com.riskplatform.gateway.domain.EngineEvaluationResult;
import com.riskplatform.gateway.domain.EngineGateway;
import com.riskplatform.gateway.domain.InvokeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 规则/决策引擎网关（REST 实现，R2.6/R16.1）。
 *
 * <p>支持 {@link InvokeMode#RULE_PACKAGE} 与 {@link InvokeMode#DECISION_FLOW} 运行时调用；
 * {@link InvokeMode#AUTO} 按事件绑定自动解析。
 */
public class RestEngineGateway implements EngineGateway {

    private static final Logger log = LoggerFactory.getLogger(RestEngineGateway.class);
    private static final String FAIL_CLOSED_DECISION = "REVIEW";

    private final RestClient restClient;
    private final String baseUrl;

    public RestEngineGateway(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
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
                    ? getBindings(eventTypeCode) : Map.of();
            InvokeMode resolved = resolveMode(invokeMode, bindings, rulePackageId, decisionFlowId);
            return switch (resolved) {
                case RULE_PACKAGE -> evaluateRulePackage(
                        eid, requirePackageId(rulePackageId, bindings), ctx);
                case DECISION_FLOW -> evaluateDecisionFlow(
                        eid, requireFlowId(decisionFlowId, bindings), ctx);
                case AUTO -> throw new IllegalStateException("AUTO must be resolved before evaluation");
            };
        } catch (Exception ex) {
            log.error("引擎评估失败，降级为 REVIEW: eventId={}, eventTypeCode={}, mode={}",
                    eid, eventTypeCode, invokeMode, ex);
            return EngineEvaluationResult.failClosed(resolvedModeName(invokeMode));
        }
    }

    private static String resolvedModeName(InvokeMode invokeMode) {
        return invokeMode == null ? "AUTO" : invokeMode.name();
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
        throw new IllegalArgumentException("未配置决策流或规则包: eventType=" + bindings);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> getBindings(String eventTypeCode) {
        return restClient.get()
                .uri(baseUrl + "/api/v1/decision-flows/bindings?eventTypeCode={code}", eventTypeCode)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private EngineEvaluationResult evaluateRulePackage(String eventId, long packageId, Map<String, Object> context) {
        Map<String, Object> body = Map.of("eventId", eventId, "context", context);
        Map<String, Object> resp = restClient.post()
                .uri(baseUrl + "/api/v1/rule-packages/{id}/evaluate", packageId)
                .body(body)
                .retrieve()
                .body(Map.class);
        String raw = resp == null || resp.get("decision") == null
                ? FAIL_CLOSED_DECISION : String.valueOf(resp.get("decision"));
        return EngineEvaluationResult.rulePackage(
                DecisionNormalizer.toBusinessDecision(raw),
                packageId,
                resp == null ? Map.of() : resp);
    }

    @SuppressWarnings("unchecked")
    private EngineEvaluationResult evaluateDecisionFlow(String eventId, long flowId, Map<String, Object> context) {
        Map<String, Object> body = Map.of("eventId", eventId, "context", context);
        Map<String, Object> resp = restClient.post()
                .uri(baseUrl + "/api/v1/decision-flows/{id}/evaluate", flowId)
                .body(body)
                .retrieve()
                .body(Map.class);
        String raw = resp == null || resp.get("decision") == null
                ? FAIL_CLOSED_DECISION : String.valueOf(resp.get("decision"));
        return EngineEvaluationResult.decisionFlow(
                DecisionNormalizer.toBusinessDecision(raw),
                flowId,
                resp == null ? Map.of() : resp);
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
        return list.stream().map(RestEngineGateway::asLong).filter(java.util.Objects::nonNull).toList();
    }
}
