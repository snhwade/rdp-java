package com.riskplatform.gateway.domain;

import java.util.Map;

/**
 * 订单落库端口（R10）。由基础设施层异步写入 MySQL（不阻塞决策返回）。
 */
public interface OrderStore {

    /** 异步落库订单业务数据与上下文（同一 eventId 至多一条，R10.1）。 */
    void persistAsync(
            String eventId,
            String businessOrderId,
            String eventTypeCode,
            Map<String, Object> context,
            long eventTimeMs);

    /** 决策产出后写入最终决策（R10.2）。 */
    void updateDecisionAsync(String eventId, String finalDecision);
}
