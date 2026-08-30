package com.riskplatform.engine.domain.rating;

/**
 * 直接定级项（引擎执行侧，直接定级用，R13.1/R13.2）。
 *
 * <p>直接定级方式下的一个可命中条目：当 {@link #condition} 对决策上下文求值为真时命中，
 * 命中即直接赋予该项配置的等级 {@link #grade}（无分值累加）。
 *
 * <p>条件 {@link #condition} 为 Aviator 布尔表达式，由 {@link DirectGrader} 经
 * {@link com.riskplatform.engine.domain.rule.RuleExpressionEvaluator} 求值，
 * 与规则/评级子项条件求值同构。
 *
 * <p>多项命中时由 {@link DirectGrader} 依据等级序 {@link GradeOrder} 取最高等级（R13.4）。
 *
 * @param condition 命中条件（Aviator 布尔表达式）
 * @param grade     命中赋予的等级（由配置侧定义，与 {@link GradeBand#grade()} 同名空间）
 */
public record DirectGradingItem(String condition, String grade) {

    /** 便捷构造：条件与等级。 */
    public static DirectGradingItem of(String condition, String grade) {
        return new DirectGradingItem(condition, grade);
    }
}
