package com.riskplatform.gateway.domain;

/** 业务系统调用风控平台的执行模式。 */
public enum InvokeMode {
    /** 按事件自动解析：优先决策流，其次规则包。 */
    AUTO,
    /** 决策流调用。 */
    DECISION_FLOW,
    /** 规则包调用。 */
    RULE_PACKAGE;

    public static InvokeMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String value = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if ("LEGACY".equals(value)) {
            return AUTO;
        }
        return InvokeMode.valueOf(value);
    }
}
