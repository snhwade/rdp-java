package com.riskplatform.gateway.domain;

import java.util.Map;

/** 从事件上下文中提取常用业务字段。 */
public final class ContextFieldSupport {

    private ContextFieldSupport() {
    }

    public static String extractMerchantId(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object v = context.get("merchantId");
        if (v == null) {
            v = context.get("merchant_id");
        }
        return v == null ? null : String.valueOf(v);
    }

    /** 业务订单号；未传时返回 null（订单维度回退为 eventId）。 */
    public static String extractBusinessOrderId(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        for (String key : new String[]{"orderId", "orderNo", "businessOrderId", "order_id", "order_no"}) {
            Object v = context.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).trim();
            }
        }
        return null;
    }
}
