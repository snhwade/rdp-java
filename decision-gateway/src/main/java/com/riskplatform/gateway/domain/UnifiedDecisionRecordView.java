package com.riskplatform.gateway.domain;

/**
 * 决策查询列表视图：引擎同步轨 + AI Agent 旁路摘要（按 eventId 关联）。
 */
public record UnifiedDecisionRecordView(
        String eventId,
        String businessOrderId,
        String correlationId,
        String merchantId,
        String eventTypeCode,
        long eventTimeMs,
        String engineDecision,
        String finalDecision,
        String invokeMode,
        Long rulePackageId,
        Long decisionFlowId,
        Long elapsedMs,
        String aiStatus,
        String agentDecision,
        Double confidence,
        Boolean divergence,
        Long aiCompletedAtMs) {
}
