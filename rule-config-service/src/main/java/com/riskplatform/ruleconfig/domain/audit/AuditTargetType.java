package com.riskplatform.ruleconfig.domain.audit;

/**
 * 审计对象类型（R17.3）。
 *
 * <p>对应 audit_log.target_type，限定为受审计的四类配置对象。
 */
public enum AuditTargetType {
    /** 事件类型。 */
    EVENT_TYPE("event_type"),
    /** 规则。 */
    RULE("rule"),
    /** 指标定义。 */
    INDICATOR("indicator");

    private final String code;

    AuditTargetType(String code) {
        this.code = code;
    }

    /** 返回落库使用的字符串编码（与 design.md 表设计一致）。 */
    public String code() {
        return code;
    }
}
