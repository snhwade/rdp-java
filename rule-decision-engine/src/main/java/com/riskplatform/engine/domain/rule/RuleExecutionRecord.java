package com.riskplatform.engine.domain.rule;

/**
 * 单条规则执行记录（R5.5）。
 *
 * @param ruleId  规则标识
 * @param version 规则版本
 * @param hit     是否命中
 * @param failed  是否执行失败（求值异常）
 * @param failReason 失败原因（无则 null）
 */
public record RuleExecutionRecord(long ruleId, int version, boolean hit, boolean failed, String failReason) {

    public static RuleExecutionRecord hit(long ruleId, int version) {
        return new RuleExecutionRecord(ruleId, version, true, false, null);
    }

    public static RuleExecutionRecord miss(long ruleId, int version) {
        return new RuleExecutionRecord(ruleId, version, false, false, null);
    }

    public static RuleExecutionRecord failed(long ruleId, int version, String reason) {
        return new RuleExecutionRecord(ruleId, version, false, true, reason);
    }
}
