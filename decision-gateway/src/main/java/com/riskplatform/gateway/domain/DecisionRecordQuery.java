package com.riskplatform.gateway.domain;

/**
 * 决策执行记录分页查询条件。
 */
public record DecisionRecordQuery(
        String eventId,
        String correlationId,
        String merchantId,
        String eventTypeCode,
        Long startTimeMs,
        Long endTimeMs,
        int page,
        int pageSize,
        String businessOrderId,
        Boolean divergenceOnly) {

    /** 兼容旧调用方（无 businessOrderId / divergence）。 */
    public DecisionRecordQuery(
            String eventId,
            String correlationId,
            String merchantId,
            String eventTypeCode,
            Long startTimeMs,
            Long endTimeMs,
            int page,
            int pageSize) {
        this(eventId, correlationId, merchantId, eventTypeCode, startTimeMs, endTimeMs, page, pageSize, null, null);
    }

    /** 兼容含 businessOrderId、无 divergence 的调用方。 */
    public DecisionRecordQuery(
            String eventId,
            String correlationId,
            String merchantId,
            String eventTypeCode,
            Long startTimeMs,
            Long endTimeMs,
            int page,
            int pageSize,
            String businessOrderId) {
        this(eventId, correlationId, merchantId, eventTypeCode, startTimeMs, endTimeMs, page, pageSize, businessOrderId, null);
    }

    public DecisionRecordQuery {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }
    }
}
