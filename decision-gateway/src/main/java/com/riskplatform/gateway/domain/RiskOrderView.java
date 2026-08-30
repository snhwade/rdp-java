package com.riskplatform.gateway.domain;

/**
 * 订单查询结果视图（R10.4）。对应 risk_order 表的对外可见字段。
 *
 * @param eventId        全局唯一事件标识
 * @param eventTypeCode  事件类型 code
 * @param merchantId     商户标识（可空）
 * @param eventTimeMs    事件时间（毫秒时间戳）
 * @param finalDecision  最终决策（PASS/REVIEW/REJECT，决策未写入时可空）
 */
public record RiskOrderView(
        String eventId,
        String businessOrderId,
        String eventTypeCode,
        String merchantId,
        long eventTimeMs,
        String finalDecision) {
}
