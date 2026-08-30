package com.riskplatform.gateway.domain;

/**
 * 订单维度分页查询条件。
 */
public record BusinessOrderQuery(
        String businessOrderId,
        String merchantId,
        String eventTypeCode,
        Long startTimeMs,
        Long endTimeMs,
        int page,
        int pageSize) {

    public BusinessOrderQuery {
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

    public boolean isTimeRangeInverted() {
        return startTimeMs != null && endTimeMs != null && startTimeMs > endTimeMs;
    }
}
