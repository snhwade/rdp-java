package com.riskplatform.gateway.application;

/**
 * 风控事件受理结果（R2.1/R6）。
 */
public record RiskEventResult(
        String eventId,
        String decision,
        String invokeMode,
        Long rulePackageId,
        Long decisionFlowId,
        java.util.Map<String, Object> detail) {

    public RiskEventResult(String eventId, String decision) {
        this(eventId, decision, "AUTO", null, null, java.util.Map.of());
    }
}
