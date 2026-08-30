package com.riskplatform.gateway.domain;

import java.util.List;
import java.util.Map;

/**
 * 单次调用（eventId）详情：引擎轨 + AI 轨。
 */
public record InvocationDetailView(
        String eventId,
        String businessOrderId,
        String correlationId,
        String merchantId,
        String eventTypeCode,
        long eventTimeMs,
        EngineDecisionRecordView engine,
        AiDecisionRecordView ai,
        List<Map<String, Object>> engineHits) {
}
