package com.riskplatform.gateway.domain;

/**
 * 订单维度列表视图：按业务订单号聚合多次风控调用。
 */
public record BusinessOrderSummaryView(
        String businessOrderId,
        String merchantId,
        String eventTypeCode,
        int invocationCount,
        long lastEventTimeMs,
        String latestFinalDecision) {
}
