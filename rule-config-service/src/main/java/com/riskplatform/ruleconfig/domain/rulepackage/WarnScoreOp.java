package com.riskplatform.ruleconfig.domain.rulepackage;

/**
 * 预警单分值阈值比较符（R4.5）。
 *
 * <p>仅当总分满足该比较条件时生成风控结果（预警单）。
 */
public enum WarnScoreOp {
    /** 大于等于阈值 */
    GTE,
    /** 小于阈值 */
    LT
}
