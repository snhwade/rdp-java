package com.riskplatform.gateway.domain;

import java.util.List;
import java.util.Map;

/**
 * AI Agent 决策执行记录视图（列表/详情）。
 */
public record AiDecisionRecordView(
        String eventId,
        String correlationId,
        String merchantId,
        String eventTypeCode,
        long eventTimeMs,
        String status,
        String agentDecision,
        Double confidence,
        String reason,
        String engineDecision,
        Boolean divergence,
        List<Map<String, Object>> trace,
        String failReason,
        long createdAtMs,
        Long completedAtMs) {
}
