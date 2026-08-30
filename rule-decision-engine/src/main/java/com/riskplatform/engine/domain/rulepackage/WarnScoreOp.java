package com.riskplatform.engine.domain.rulepackage;

import java.math.BigDecimal;

/**
 * 评分模式预警单阈值运算符（R4.5）。
 *
 * <p>当规则包开启「生成预警单分值阈值」时，仅当总分满足阈值条件才生成风控结果（预警单）。
 *
 * <ul>
 *   <li>{@link #GTE} 大于等于：总分 &gt;= 阈值 时生成。</li>
 *   <li>{@link #LT} 小于：总分 &lt; 阈值 时生成。</li>
 * </ul>
 */
public enum WarnScoreOp {
    /** 大于等于阈值时生成预警单。 */
    GTE {
        @Override
        public boolean test(BigDecimal totalScore, BigDecimal threshold) {
            return totalScore.compareTo(threshold) >= 0;
        }
    },
    /** 小于阈值时生成预警单。 */
    LT {
        @Override
        public boolean test(BigDecimal totalScore, BigDecimal threshold) {
            return totalScore.compareTo(threshold) < 0;
        }
    };

    /**
     * 判定总分是否满足预警阈值条件。
     *
     * @param totalScore 评分模式累加总分
     * @param threshold  配置的阈值
     * @return 满足返回 true（应生成预警单）
     */
    public abstract boolean test(BigDecimal totalScore, BigDecimal threshold);
}
