package com.riskplatform.engine.domain.strategy;

import java.util.List;

/**
 * 策略聚合结果（命中模式 / 评分模式共用）。
 *
 * <p>聚合后输出的策略列表；评分模式额外携带命中区间的风险等级。
 * 输出按类别归并后的最终策略集合，省略无策略的类别（不含空块，R3.7）。
 *
 * @param strategies    聚合后输出的策略列表
 * @param riskLevelCode 风险等级编码（评分模式命中区间时给出；命中模式为 null）
 */
public record StrategyAggregateResult(List<StrategyItem> strategies, String riskLevelCode) {
}
