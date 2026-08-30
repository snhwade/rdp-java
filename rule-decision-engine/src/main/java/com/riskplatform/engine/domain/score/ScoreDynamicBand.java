package com.riskplatform.engine.domain.score;

import java.math.BigDecimal;

/**
 * 评分规则动态分区间（引擎执行侧轻量 DTO，R4.2/R4.6）。
 *
 * <p>与配置侧 {@code rule_dynamic_score} / {@code DynamicScoreBand} 同构（精简至执行所需）：
 * 当评分标准指标 {@link #indicatorRefName} 的取值落入区间时，取 {@link #score} 作为动态得分。
 *
 * <p>区间开闭边界由 {@link #lowerInclusive}/{@link #upperInclusive} 决定，默认左闭右开
 * （lowerInclusive=true、upperInclusive=false，R4.6 确定性归入）。{@link #lower}/{@link #upper}
 * 为空分别表示负无穷/正无穷。
 *
 * @param indicatorRefName 评分标准指标引用名（在决策上下文 Map 中的键）
 * @param lower            区间下界（null 表示负无穷）
 * @param upper            区间上界（null 表示正无穷）
 * @param lowerInclusive   下界是否包含
 * @param upperInclusive   上界是否包含
 * @param score            该区间动态得分
 */
public record ScoreDynamicBand(String indicatorRefName,
                               BigDecimal lower,
                               BigDecimal upper,
                               boolean lowerInclusive,
                               boolean upperInclusive,
                               BigDecimal score) {

    /**
     * 判断给定指标值是否落入本区间（按开闭边界精确判定）。
     *
     * @param value 指标值（已转为 BigDecimal）
     * @return 落入返回 true
     */
    public boolean contains(BigDecimal value) {
        if (value == null) {
            return false;
        }
        if (lower != null) {
            int c = value.compareTo(lower);
            if (c < 0 || (c == 0 && !lowerInclusive)) {
                return false;
            }
        }
        if (upper != null) {
            int c = value.compareTo(upper);
            if (c > 0 || (c == 0 && !upperInclusive)) {
                return false;
            }
        }
        return true;
    }
}
