package com.riskplatform.gateway.domain;

import java.util.List;
import java.util.Map;

/** 引擎决策结果（规则包 / 决策流调用模式）。 */
public record EngineEvaluationResult(
        String decision,
        String invokeMode,
        Long rulePackageId,
        Long decisionFlowId,
        Map<String, Object> detail) {

    public static EngineEvaluationResult rulePackage(String decision, long packageId, Map<String, Object> detail) {
        return new EngineEvaluationResult(decision, "RULE_PACKAGE", packageId, null, detail);
    }

    public static EngineEvaluationResult decisionFlow(String decision, long flowId, Map<String, Object> detail) {
        return new EngineEvaluationResult(decision, "DECISION_FLOW", null, flowId, detail);
    }

    /** 引擎不可用或配置缺失时的 fail-closed 结果（人工复核）。 */
    public static EngineEvaluationResult failClosed(String invokeMode) {
        return new EngineEvaluationResult("REVIEW", invokeMode, null, null, Map.of("failClosed", true));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> hits() {
        Object raw = detail == null ? null : detail.get("hits");
        if (raw instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }
}
