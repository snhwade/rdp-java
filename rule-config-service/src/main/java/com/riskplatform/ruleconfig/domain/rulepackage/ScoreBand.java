package com.riskplatform.ruleconfig.domain.rulepackage;

import com.riskplatform.common.error.ValidationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 评分模式分值区间值对象（R1.6 / R4.4）。
 *
 * <p>对应表 {@code rule_package_score_band}。一个区间由下界 {@link #lower}、上界 {@link #upper}
 * 与各自的开闭标志（{@link #lowerInclusive}/{@link #upperInclusive}）确定，支持负分。
 *
 * <p>语义约定：
 * <ul>
 *   <li>{@code lower == null} 表示负无穷；{@code upper == null} 表示正无穷（无穷端按开区间处理）；</li>
 *   <li>默认推荐「左闭右开」（lowerInclusive=true、upperInclusive=false），与动态分区间一致，
 *       保证落点确定性（R4.6），但本值对象通过 inclusive 标志支持任意开闭组合；</li>
 *   <li>区间必须非空：下界严格小于上界；当下界等于上界时仅在两端均闭合（单点区间）时合法。</li>
 * </ul>
 *
 * <p>值对象不可变，所有字段在构造时校验。
 */
public final class ScoreBand {

    /** 下界；null 表示负无穷。 */
    private final BigDecimal lower;
    /** 上界；null 表示正无穷。 */
    private final BigDecimal upper;
    /** 下界是否包含（闭）。 */
    private final boolean lowerInclusive;
    /** 上界是否包含（闭）。 */
    private final boolean upperInclusive;
    /** 映射风险等级编码。 */
    private final String riskLevelCode;
    /** 排序号。 */
    private final int orderNo;

    private ScoreBand(BigDecimal lower, BigDecimal upper, boolean lowerInclusive,
                      boolean upperInclusive, String riskLevelCode, int orderNo) {
        this.lower = lower;
        this.upper = upper;
        this.lowerInclusive = lowerInclusive;
        this.upperInclusive = upperInclusive;
        this.riskLevelCode = riskLevelCode;
        this.orderNo = orderNo;
    }

    /** 工厂方法：创建并校验单个分值区间。 */
    public static ScoreBand of(BigDecimal lower, BigDecimal upper, boolean lowerInclusive,
                               boolean upperInclusive, String riskLevelCode, int orderNo) {
        ScoreBand band = new ScoreBand(lower, upper, lowerInclusive, upperInclusive, riskLevelCode, orderNo);
        band.validate();
        return band;
    }

    /** 校验区间自身的合法性（非空区间）。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (lower != null && upper != null) {
            int cmp = lower.compareTo(upper);
            if (cmp > 0) {
                errors.field("scoreBand", "区间下界不能大于上界");
            } else if (cmp == 0 && !(lowerInclusive && upperInclusive)) {
                // 下界等于上界时只有 [x, x] 单点闭区间才非空
                errors.field("scoreBand", "区间为空：下界等于上界时两端必须均为闭合");
            }
        }
        errors.throwIfAny();
    }

    /**
     * 判定给定分值是否落入本区间（含开闭与无穷端语义）。
     *
     * @param score 待判定分值（非空）
     */
    public boolean contains(BigDecimal score) {
        Objects.requireNonNull(score, "score");
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

    /**
     * 判定本区间与另一区间是否存在重叠（存在同时落入两区间的实数值即为重叠）。
     *
     * <p>实现：两区间不重叠当且仅当其一完全位于另一之左（本区间上界 &le; 对方下界，
     * 且边界点不同时闭合）。否则视为重叠。无穷端永不构成分隔。
     */
    public boolean overlaps(ScoreBand other) {
        Objects.requireNonNull(other, "other");
        // this 完全在 other 左侧：this.upper 与 other.lower 处分隔
        if (isLeftOf(this, other)) {
            return false;
        }
        // other 完全在 this 左侧
        if (isLeftOf(other, this)) {
            return false;
        }
        return true;
    }

    /**
     * 判定区间 a 是否完全位于区间 b 之左（两者无交集且 a 在前）。
     *
     * <p>条件：a.upper 与 b.lower 存在，且 a.upper &lt; b.lower，
     * 或 a.upper == b.lower 但该边界点不同时被两区间包含（至少一端为开）。
     */
    private static boolean isLeftOf(ScoreBand a, ScoreBand b) {
        if (a.upper == null || b.lower == null) {
            // a 向正无穷延伸，或 b 向负无穷延伸：无法在此侧分隔
            return false;
        }
        int cmp = a.upper.compareTo(b.lower);
        if (cmp < 0) {
            return true;
        }
        if (cmp == 0) {
            // 边界点重合：仅当两端不同时闭合（即不共享该点）时才算分隔
            return !(a.upperInclusive && b.lowerInclusive);
        }
        return false;
    }

    /**
     * 校验一组分值区间两两不重叠（R1.6）。重叠时抛出 {@link ValidationException}。
     *
     * @param bands 待校验区间列表（null/单元素视为合法）
     */
    public static void validateNonOverlapping(List<ScoreBand> bands) {
        if (bands == null || bands.size() < 2) {
            return;
        }
        ValidationException.Builder errors = ValidationException.builder();
        for (int i = 0; i < bands.size(); i++) {
            for (int j = i + 1; j < bands.size(); j++) {
                if (bands.get(i).overlaps(bands.get(j))) {
                    errors.field("scoreBands", "分值区间存在重叠：第 " + (i + 1) + " 项与第 " + (j + 1) + " 项");
                    errors.throwIfAny();
                }
            }
        }
        errors.throwIfAny();
    }

    public BigDecimal getLower() {
        return lower;
    }

    public BigDecimal getUpper() {
        return upper;
    }

    public boolean isLowerInclusive() {
        return lowerInclusive;
    }

    public boolean isUpperInclusive() {
        return upperInclusive;
    }

    public String getRiskLevelCode() {
        return riskLevelCode;
    }

    public int getOrderNo() {
        return orderNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScoreBand)) {
            return false;
        }
        ScoreBand that = (ScoreBand) o;
        return lowerInclusive == that.lowerInclusive
                && upperInclusive == that.upperInclusive
                && orderNo == that.orderNo
                && Objects.equals(lower, that.lower)
                && Objects.equals(upper, that.upper)
                && Objects.equals(riskLevelCode, that.riskLevelCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lower, upper, lowerInclusive, upperInclusive, riskLevelCode, orderNo);
    }
}
