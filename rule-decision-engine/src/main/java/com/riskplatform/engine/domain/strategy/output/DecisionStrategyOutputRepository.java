package com.riskplatform.engine.domain.strategy.output;

import java.util.List;

/**
 * 决策产出策略记录仓储端口（R3.4/R3.5）。
 *
 * <p>由基础设施层用 MyBatis-Plus 持久化到 {@code decision_strategy_output} 表。
 * 端口仅负责「记录」——保存与查询，<b>不包含任何外部系统下发动作</b>。
 */
public interface DecisionStrategyOutputRepository {

    /**
     * 批量保存一次决策产出的策略记录。
     *
     * @param records 待落库的策略记录列表（可为空，空时直接返回）
     */
    void saveAll(List<DecisionStrategyRecord> records);

    /** 按事件标识查询该事件产出的全部策略记录（用于随响应返回/链路查询）。 */
    List<DecisionStrategyRecord> findByEventId(String eventId);

    /** 按决策日志 ID 查询产出的策略记录。 */
    List<DecisionStrategyRecord> findByDecisionId(Long decisionId);
}
