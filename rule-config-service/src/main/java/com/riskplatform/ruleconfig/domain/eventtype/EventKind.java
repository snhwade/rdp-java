package com.riskplatform.ruleconfig.domain.eventtype;

/**
 * 事件类型分型（risk-console-redesign R2.4）。
 *
 * <p>事件的数据建模分型，取「维度表」与「事实表」二选一（必填其一）。
 */
public enum EventKind {
    /** 维度表。 */
    DIMENSION,
    /** 事实表。 */
    FACT
}
