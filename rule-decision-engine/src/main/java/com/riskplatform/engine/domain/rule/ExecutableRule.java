package com.riskplatform.engine.domain.rule;

import com.riskplatform.engine.domain.decision.Decision;

/**
 * 可执行规则（R5）。
 *
 * <p>{@code trialRun} 标识规则三态语义中的「试运行」状态（R7.5/R7.7）：试运行规则会被执行并在
 * 命中明细中返回，但其命中不参与最终决策聚合（R7.6）。既有调用方默认构造（不带该参数）按上线
 * （{@code trialRun=false}）处理，保持向后兼容。
 *
 * @param ruleId        规则标识
 * @param version       规则版本
 * @param priority      组内优先级（数值越小优先级越高 R5.1）
 * @param expression    Aviator 表达式
 * @param decision      命中时产出的决策
 * @param shortCircuited 命中后是否短路（停止更低优先级规则 R5.6）
 * @param trialRun      是否为试运行规则（true=试运行，false=上线）
 */
public record ExecutableRule(
        long ruleId,
        int version,
        int priority,
        String expression,
        Decision decision,
        boolean shortCircuited,
        boolean trialRun) {

    /** 向后兼容构造：默认按上线规则处理（{@code trialRun=false}）。 */
    public ExecutableRule(long ruleId,
                          int version,
                          int priority,
                          String expression,
                          Decision decision,
                          boolean shortCircuited) {
        this(ruleId, version, priority, expression, decision, shortCircuited, false);
    }
}
