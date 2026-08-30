package com.riskplatform.ruleconfig.domain.rulev2;

import com.riskplatform.common.error.ValidationException;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评分规则动态分区间值对象（R4.2/R4.6）。
 *
 * <p>对应表 {@code rule_dynamic_score} 的一行：在指标 {@code indicatorRefName} 取值落入
 * {@code [lower, upper)}（默认左闭右开）区间时取 {@code score} 作为动态得分。
 *
 * <p>不变式：
 * <ul>
 *   <li>区间至少有一侧有界（下界与上界不可同时为空）。</li>
 *   <li>下界与上界均存在时下界不得大于上界。</li>
 *   <li>得分必填。</li>
 *   <li>同一规则内各区间两两不重叠（由 {@link #validateNonOverlapping(List)} 保证，R4.6）。</li>
 * </ul>
 *
 * @param indicatorRefName 评分标准指标引用名
 * @param lower            区间下界（可空表示负无穷）
 * @param upper            区间上界（可空表示正无穷）
 * @param lowerInclusive   下界是否包含（默认 true）
 * @param upperInclusive   上界是否包含（默认 false）
 * @param score            该区间动态得分
 * @param orderNo          排序号
 */
public record DynamicScoreBand(String indicatorRefName,
                               BigDecimal lower,
                               BigDecimal upper,
                               boolean lowerInclusive,
                               boolean upperInclusive,
                               BigDecimal score,
                               int orderNo) {

    public DynamicScoreBand {
        ValidationException.Builder errors = ValidationException.builder();
        if (indicatorRefName == null || indicatorRefName.isBlank()) {
            errors.field("dynamicScore.indicatorRefName", "评分标准指标引用名必填");
        }
        if (lower == null && upper == null) {
            errors.field("dynamicScore.range", "区间下界与上界不能同时为空");
        }
        if (lower != null && upper != null && lower.compareTo(upper) > 0) {
            errors.field("dynamicScore.range", "区间下界不能大于上界");
        }
        if (score == null) {
            errors.field("dynamicScore.score", "动态得分必填");
        }
        errors.throwIfAny();
    }

    /** 工厂方法。 */
    public static DynamicScoreBand of(String indicatorRefName, BigDecimal lower, BigDecimal upper,
                                      boolean lowerInclusive, boolean upperInclusive,
                                      BigDecimal score, int orderNo) {
        return new DynamicScoreBand(indicatorRefName, lower, upper, lowerInclusive, upperInclusive, score, orderNo);
    }

    /**
     * 校验同一规则内动态分区间两两不重叠（R4.6）。
     *
     * <p>采用区间相交判定：两区间不重叠当且仅当一个的上界 ≤ 另一个的下界（按开闭边界精确判定）。
     * 任意一侧无界（null）视为 ±∞，与其它区间在该侧方向恒相交。
     *
     * @param bands 动态分区间列表
     * @throws ValidationException 存在重叠区间时抛出
     */
    public static void validateNonOverlapping(List<DynamicScoreBand> bands) {
        if (bands == null || bands.size() < 2) {
            return;
        }
        for (int i = 0; i < bands.size(); i++) {
            for (int j = i + 1; j < bands.size(); j++) {
                if (overlaps(bands.get(i), bands.get(j))) {
                    ValidationException.builder()
                            .field("dynamicScore.range", "动态分区间存在重叠：第 " + i + " 与第 " + j + " 个区间")
                            .throwIfAny();
                }
            }
        }
    }

    /** 判断两个区间是否相交（有重叠）。 */
    private static boolean overlaps(DynamicScoreBand a, DynamicScoreBand b) {
        // a 完全在 b 左侧（不相交）：a.upper <= b.lower
        if (isLeftOf(a.upper, a.upperInclusive, b.lower, b.lowerInclusive)) {
            return false;
        }
        // b 完全在 a 左侧（不相交）：b.upper <= a.lower
        if (isLeftOf(b.upper, b.upperInclusive, a.lower, a.lowerInclusive)) {
            return false;
        }
        return true;
    }

    /**
     * 判断左区间上界是否完全在右区间下界左侧（即两区间在此处不接触）。
     *
     * @param upper          左区间上界（null 表示 +∞）
     * @param upperInclusive 左区间上界是否包含
     * @param lower          右区间下界（null 表示 -∞）
     * @param lowerInclusive 右区间下界是否包含
     */
    private static boolean isLeftOf(BigDecimal upper, boolean upperInclusive,
                                    BigDecimal lower, boolean lowerInclusive) {
        if (upper == null || lower == null) {
            return false;
        }
        int cmp = upper.compareTo(lower);
        if (cmp < 0) {
            return true;
        }
        // 边界相等时：仅当两侧都包含才相接（相交于一点），否则视为不相交
        if (cmp == 0) {
            return !(upperInclusive && lowerInclusive);
        }
        return false;
    }
}
