package com.riskplatform.engine.domain.rulepackage;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rule.ExecutableRule;
import com.riskplatform.engine.domain.score.ScoreRule;
import com.riskplatform.engine.domain.strategy.StrategyItem;

import java.util.List;

/**
 * 规则包内的一条可执行规则（命中/评分两模式共用的执行侧轻量 DTO）。
 *
 * <p>每条规则均持有一个已编译的 Aviator 条件表达式 {@link #expression}，用于判定该规则在
 * 当前决策上下文下是否被触发（命中）。两种触发模式对其余字段的使用不同：
 *
 * <ul>
 *   <li><b>命中模式（HIT）</b>：使用 {@link #decision}（命中产出决策）、{@link #shortCircuited}
 *       （命中后是否短路）、{@link #strategies}（命中后该规则绑定的策略，供策略聚合）。</li>
 *   <li><b>评分模式（SCORE）</b>：使用 {@link #scoreRule}（基础分 + 动态分配置）；规则被触发时
 *       由 {@link com.riskplatform.engine.domain.score.ScoreCalculator} 计算其触发分并累加。
 *       此模式下不短路（执行器内部以非短路方式收集全部触发规则）。</li>
 * </ul>
 *
 * @param ruleId         规则标识
 * @param version        规则版本（用于追溯）
 * @param priority       组内优先级（数值越小优先级越高，R5.1）
 * @param expression     已编译的 Aviator 条件表达式（判定是否触发）
 * @param decision       命中模式：命中产出的决策（评分模式可为 null）
 * @param shortCircuited 命中模式：命中后是否短路（评分模式忽略）
 * @param strategies     命中模式：该规则绑定的策略列表（评分模式可为 null）
 * @param scoreRule      评分模式：该规则的评分配置（命中模式可为 null）
 * @param trialRun       规则三态：是否为试运行规则（R7.5/R7.7）。试运行规则会被执行并在命中明细
 *                       中返回，但不参与最终决策聚合（R7.6）。
 */
public record RulePackageRule(long ruleId,
                              int version,
                              int priority,
                              String expression,
                              Decision decision,
                              boolean shortCircuited,
                              List<StrategyItem> strategies,
                              ScoreRule scoreRule,
                              boolean trialRun) {

    public RulePackageRule {
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
    }

    /** 向后兼容构造：默认按上线规则处理（{@code trialRun=false}）。 */
    public RulePackageRule(long ruleId,
                           int version,
                           int priority,
                           String expression,
                           Decision decision,
                           boolean shortCircuited,
                           List<StrategyItem> strategies,
                           ScoreRule scoreRule) {
        this(ruleId, version, priority, expression, decision, shortCircuited,
                strategies, scoreRule, false);
    }

    /**
     * 转换为规则执行内核可消费的 {@link ExecutableRule}。
     *
     * @param disableShortCircuit 是否强制关闭短路（评分模式收集全部触发规则时传 true）
     * @return 可执行规则
     */
    public ExecutableRule toExecutableRule(boolean disableShortCircuit) {
        Decision effectiveDecision = decision == null ? Decision.PASS : decision;
        boolean effectiveShortCircuit = !disableShortCircuit && shortCircuited;
        return new ExecutableRule(ruleId, version, priority, expression,
                effectiveDecision, effectiveShortCircuit, trialRun);
    }
}
