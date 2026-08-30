package com.riskplatform.gateway.domain;

import java.util.Map;

/**
 * 引擎决策执行记录视图（列表/详情）。
 */
public record EngineDecisionRecordView(
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
        Long elapsedMs,
        long createdAtMs) {
}
