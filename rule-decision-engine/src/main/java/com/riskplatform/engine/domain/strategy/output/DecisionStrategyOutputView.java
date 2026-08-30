package com.riskplatform.engine.domain.strategy.output;

import com.riskplatform.engine.domain.strategy.StrategyCategory;

import java.util.List;
import java.util.Map;

/**
 * 随决策响应返回的策略产出视图（R3.5/R3.7）。
 *
 * <p>对一次决策产出的策略记录做轻量呈现。明确标注 {@link #dispatched} 恒为 {@code false}，
 * 表达平台「只记录不下发」的边界——名单策略也仅记录意图。
 *
 * @param eventId    事件标识
 * @param decisionId 关联决策日志 ID（可为 null）
 * @param strategies 本次产出的策略项视图列表
 * @param dispatched 是否已向外部系统下发（恒为 false：平台只记录不下发）
 */
public record DecisionStrategyOutputView(String eventId,
                                         Long decisionId,
                                         List<Item> strategies,
                                         boolean dispatched) {

    /**
     * 单条策略项视图。
     *
     * @param category     策略类别
     * @param strategyCode 策略编码
     * @param ruleV2Id     命中规则 ID（null=区间映射产出）
     * @param payload      本次输出的具体参数（只记录不下发）
     */
    public record Item(StrategyCategory category,
                       String strategyCode,
                       Long ruleV2Id,
                       Map<String, Object> payload) {
    }
}
