package com.riskplatform.ruleconfig.domain.eventtype;

/**
 * 事件用途（risk-console-redesign R2.3）。
 *
 * <p>事件可被用于计算与/或决策，<b>可多选</b>，但至少选择一个（非空子集，Property 4）。
 */
public enum EventPurpose {
    /** 计算用途。 */
    COMPUTE,
    /** 决策用途。 */
    DECISION
}
