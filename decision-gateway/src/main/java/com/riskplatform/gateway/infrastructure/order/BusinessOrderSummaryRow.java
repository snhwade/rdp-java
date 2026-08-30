package com.riskplatform.gateway.infrastructure.order;

import java.time.LocalDateTime;

/** 订单维度聚合查询行（MyBatis 映射）。 */
public class BusinessOrderSummaryRow {

    private String businessOrderId;
    private String merchantId;
    private String eventTypeCode;
    private Long invocationCount;
    private LocalDateTime lastEventTime;
    private String latestFinalDecision;

    public String getBusinessOrderId() {
        return businessOrderId;
    }

    public void setBusinessOrderId(String businessOrderId) {
        this.businessOrderId = businessOrderId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
    }

    public Long getInvocationCount() {
        return invocationCount;
    }

    public void setInvocationCount(Long invocationCount) {
        this.invocationCount = invocationCount;
    }

    public LocalDateTime getLastEventTime() {
        return lastEventTime;
    }

    public void setLastEventTime(LocalDateTime lastEventTime) {
        this.lastEventTime = lastEventTime;
    }

    public String getLatestFinalDecision() {
        return latestFinalDecision;
    }

    public void setLatestFinalDecision(String latestFinalDecision) {
        this.latestFinalDecision = latestFinalDecision;
    }
}
