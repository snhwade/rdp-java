package com.riskplatform.engine.domain.strategy;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评分模式分值区间（R1.3/R4.4）。区间之间不重叠，可含负分区间。
 *
 * <p>开闭由 {@link #lowerInclusive}/{@link #upperInclusive} 标志确定（默认左闭右开，
 * 保证边界值唯一归入一个区间，R4.6）。下界/上界为 null 表示该侧无界（-∞ / +∞）。
 *
 * @param lower          下界（null 表示负无穷）
 * @param upper          上界（null 表示正无穷）
 * @param lowerInclusive 下界是否包含
 * @param upperInclusive 上界是否包含
 * @param riskLevelCode  命中该区间对应的风险等级编码
 * @param strategies     命中该区间输出的策略列表
 */
public record ScoreBand(BigDecimal lower,
                        BigDecimal upper,
                        boolean lowerInclusive,
                        boolean upperInclusive,
                        String riskLevelCode,
                        List<StrategyItem> strategies) {

    /**
     * 判断总分是否落入本区间。
     *
     * @param score 总分
     * @return 落入返回 true
     */
    public boolean contains(BigDecimal score) {
        if (lower != null) {
            int c = score.compareTo(lower);
            if (c < 0 || (c == 0 && !lowerInclusive)) {
                return false;
            }
        }
        if (upper != null) {
            int c = score.compareTo(upper);
            if (c > 0 || (c == 0 && !upperInclusive)) {
                return false;
            }
        }
        return true;
    }
}
