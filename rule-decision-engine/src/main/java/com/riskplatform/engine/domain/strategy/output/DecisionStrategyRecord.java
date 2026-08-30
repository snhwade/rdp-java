package com.riskplatform.engine.domain.strategy.output;

import com.riskplatform.engine.domain.strategy.StrategyCategory;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 决策产出的单条策略记录（对应 decision_strategy_output 一行，R3.4/R3.5）。
 *
 * <p>表示「某次决策命中规则或评分区间映射产出的一条策略」。本记录是纯领域对象，
 * 仅用于落库与随响应返回，<b>不携带任何下发/执行语义</b>——平台只记录意图，不对接外部系统。
 *
 * @param id           主键（落库后回填，构造待存记录时为 null）
 * @param eventId      事件标识
 * @param decisionId   关联决策日志 ID（decision_log.id，可为 null）
 * @param ruleV2Id     命中规则 ID；为 {@code null} 表示由评分区间映射产出
 * @param category     策略类别
 * @param strategyCode 策略编码
 * @param payload      本次输出的具体参数（通知渠道/验证方式/目标名单等，只记录不下发）
 * @param createdAt    产出时间（构造待存记录时可为 null，由仓储补齐）
 */
public record DecisionStrategyRecord(Long id,
                                     String eventId,
                                     Long decisionId,
                                     Long ruleV2Id,
                                     StrategyCategory category,
                                     String strategyCode,
                                     Map<String, Object> payload,
                                     LocalDateTime createdAt) {

    /** 构造一条「待落库」的新记录（id 与 createdAt 留空，由仓储补齐）。 */
    public static DecisionStrategyRecord pending(String eventId,
                                                 Long decisionId,
                                                 Long ruleV2Id,
                                                 StrategyCategory category,
                                                 String strategyCode,
                                                 Map<String, Object> payload) {
        return new DecisionStrategyRecord(null, eventId, decisionId, ruleV2Id,
                category, strategyCode, payload, null);
    }
}
