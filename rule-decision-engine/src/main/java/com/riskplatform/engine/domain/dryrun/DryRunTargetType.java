package com.riskplatform.engine.domain.dryrun;

/**
 * 试运行目标类型（R5.1）。
 *
 * <ul>
 *   <li>{@link #RULE} 单条结构化规则（rule_v2）。</li>
 *   <li>{@link #RULE_PACKAGE} 规则包（rule_package，命中/评分模式）。</li>
 * </ul>
 */
public enum DryRunTargetType {
    /** 单条结构化规则。 */
    RULE,
    /** 规则包。 */
    RULE_PACKAGE
}
