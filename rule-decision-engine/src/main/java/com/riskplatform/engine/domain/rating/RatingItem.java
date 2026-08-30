package com.riskplatform.engine.domain.rating;

import java.math.BigDecimal;

/**
 * 评级子项（引擎执行侧，评分定级用，R12.1/R12.2）。
 *
 * <p>评分定级方式下的一个可命中条目：当 {@link #condition} 对决策上下文求值为真时命中，
 * 命中后按 {@link #score} 计入分值，并在累计达到 {@link #subItemCap} 时封顶（R12.2）。
 *
 * <p>条件 {@link #condition} 为 Aviator 布尔表达式，由 {@link ScoreBasedGrader} 经
 * {@link com.riskplatform.engine.domain.rule.RuleExpressionEvaluator} 求值，
 * 与规则/选择器条件求值同构。
 *
 * @param category   评级类别（可空，仅用于结果回显）
 * @param subItem    评级子项名（可空，仅用于结果回显）
 * @param condition  命中条件（Aviator 布尔表达式）
 * @param score      命中计入分值（null 视为 0）
 * @param subItemCap 子项分值上限（null 表示不封顶）
 * @param importance 子项重要程度（可空，仅用于结果回显）
 */
public record RatingItem(String category,
                         String subItem,
                         String condition,
                         BigDecimal score,
                         BigDecimal subItemCap,
                         String importance) {

    /** 便捷构造：仅条件与分值（无封顶与回显属性）。 */
    public static RatingItem of(String condition, BigDecimal score) {
        return new RatingItem(null, null, condition, score, null, null);
    }

    /** 便捷构造：条件、分值与封顶上限。 */
    public static RatingItem of(String condition, BigDecimal score, BigDecimal subItemCap) {
        return new RatingItem(null, null, condition, score, subItemCap, null);
    }

    /** 命中计入分值（null 视为 0），应用子项分值上限封顶（R12.2）。 */
    public BigDecimal cappedScore() {
        BigDecimal raw = score == null ? BigDecimal.ZERO : score;
        if (subItemCap != null && raw.compareTo(subItemCap) > 0) {
            return subItemCap;
        }
        return raw;
    }
}
