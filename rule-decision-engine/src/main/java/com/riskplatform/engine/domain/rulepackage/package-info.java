/**
 * 规则包执行编排（domain 层，R1.2/R1.3/R4.4/R4.5）。
 *
 * <p>{@link com.riskplatform.engine.domain.rulepackage.RulePackageExecutor} 编排命中/评分两种触发模式：
 * 复用 {@code RuleExecutor}（命中/触发判定 + 短路）、{@code StrategyAggregator}（策略聚合/区间映射）、
 * {@code ScoreCalculator}（触发分）、{@code DecisionAggregator}（命中模式决策推导），
 * 评分模式经 {@link com.riskplatform.engine.domain.rulepackage.RiskLevelDecisionMapper} 由风险等级推导决策。
 *
 * <p>均为无状态、可注入组件，便于复用与测试。
 */
package com.riskplatform.engine.domain.rulepackage;
