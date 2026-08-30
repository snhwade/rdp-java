package com.riskplatform.ruleconfig.domain.rulepackage;

/**
 * 规则包触发模式（R1.1）。
 *
 * <p>触发模式决定规则包的决策范式，<b>创建后不可变更</b>（由聚合根 {@code RulePackage} 保证）。
 */
public enum TriggerMode {
    /** 命中模式：命中即按策略聚合规则输出（R1.2）。 */
    HIT,
    /** 评分模式：累加触发分并按分值区间映射风险等级与策略（R1.3）。 */
    SCORE
}
