package com.riskplatform.ruleconfig.domain.rulev2;

/**
 * 结构化规则类型（R2/R4）。
 *
 * <p>对应表 {@code rule_v2.rule_kind}：
 * <ul>
 *   <li>{@link #HIT}：命中规则，命中即产出策略，不参与评分累加。</li>
 *   <li>{@link #SCORE}：评分规则，触发分（基础分 + 动态分）参与规则包评分累加（R4）。</li>
 * </ul>
 */
public enum RuleKind {
    /** 命中规则。 */
    HIT,
    /** 评分规则。 */
    SCORE
}
