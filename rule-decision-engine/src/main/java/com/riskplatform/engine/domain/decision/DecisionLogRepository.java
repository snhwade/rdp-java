package com.riskplatform.engine.domain.decision;

import java.util.Optional;

/**
 * 决策日志仓储端口（R15.1/R15.3）。
 *
 * <p>由基础设施层用 MyBatis-Plus 持久化到 decision_log 表。
 * 抽离为端口以便决策结果查询逻辑可独立测试。
 */
public interface DecisionLogRepository {

    /** 保存一条决策日志。 */
    void save(DecisionLog log);

    /** 按事件标识查询决策日志（执行链路 R15.3）。 */
    Optional<DecisionLog> findByEventId(String eventId);
}
