package com.riskplatform.engine.application;

import com.riskplatform.engine.domain.decision.DecisionLog;
import com.riskplatform.engine.domain.decision.DecisionLogRepository;

import java.util.Optional;

/**
 * 决策日志应用服务（R6.6/R15.1/R15.3）：记录决策日志、按事件标识查询执行链路。
 */
public class DecisionLogService {

    private final DecisionLogRepository repository;

    public DecisionLogService(DecisionLogRepository repository) {
        this.repository = repository;
    }

    /** 记录一次最终决策的完整日志。 */
    public void record(DecisionLog log) {
        repository.save(log);
    }

    /** 按事件标识查询决策结果与执行链路。 */
    public Optional<DecisionLog> query(String eventId) {
        return repository.findByEventId(eventId);
    }
}
