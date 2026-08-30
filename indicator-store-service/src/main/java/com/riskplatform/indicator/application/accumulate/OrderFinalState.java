package com.riskplatform.indicator.application.accumulate;

import java.util.Map;

/**
 * 订单终态数据（业务方推送至 Kafka 的消息载体，新方案消费侧）。
 *
 * <p>{@code orderId} 用于幂等去重；{@code fields} 承载累计脚本所需的业务字段，
 * 由指标定义的统计维度从中提取维度键。
 */
public class OrderFinalState {

    private String orderId;
    private String eventTypeCode;
    private long eventEpochMs;
    private Map<String, Object> fields;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
    }

    public long getEventEpochMs() {
        return eventEpochMs;
    }

    public void setEventEpochMs(long eventEpochMs) {
        this.eventEpochMs = eventEpochMs;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
}
