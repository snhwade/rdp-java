package com.riskplatform.engine.domain.rulepackage;

/**
 * 规则包触发模式（R1.1）。创建后不可变更。
 *
 * <ul>
 *   <li>{@link #HIT} 命中模式：规则命中即出策略，按命中规则集聚合策略并做决策聚合。</li>
 *   <li>{@link #SCORE} 评分模式：累加各触发规则的触发分，按分值区间映射风险等级与策略。</li>
 * </ul>
 */
public enum TriggerMode {
    /** 命中模式。 */
    HIT,
    /** 评分模式。 */
    SCORE
}
