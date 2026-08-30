package com.riskplatform.engine.domain.strategy;

import java.util.List;

/**
 * 一条命中规则及其绑定的策略列表（命中模式聚合输入，R1.2）。
 *
 * @param ruleId     命中规则标识
 * @param strategies 该规则绑定的策略列表（四类混合，可空/可多）
 */
public record HitRuleStrategies(long ruleId, List<StrategyItem> strategies) {
}
