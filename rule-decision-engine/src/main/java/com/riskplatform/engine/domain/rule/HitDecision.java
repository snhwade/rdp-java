package com.riskplatform.engine.domain.rule;

import com.riskplatform.engine.domain.decision.Decision;

/**
 * 一条命中规则产生的决策（供决策聚合使用，R6）。
 *
 * <p>{@code trialRun} 标识该命中来源规则是否为「试运行」状态（规则三态语义 R7.5/R7.7）：
 * 试运行命中会进入执行结果命中明细以供观察，但<strong>不参与最终决策聚合</strong>（R7.6）。
 * 既有调用方默认构造（不带该参数）按上线（{@code trialRun=false}）处理，保持向后兼容。
 *
 * @param ruleId   规则标识
 * @param priority 决策优先级（数值越小优先级越高 R6.1）
 * @param decision 决策结论
 * @param trialRun 命中来源规则是否为试运行（true=试运行，false=上线）
 */
public record HitDecision(long ruleId, int priority, Decision decision, boolean trialRun) {

    /** 向后兼容构造：默认按上线规则命中处理（{@code trialRun=false}）。 */
    public HitDecision(long ruleId, int priority, Decision decision) {
        this(ruleId, priority, decision, false);
    }
}
