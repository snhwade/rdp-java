package com.riskplatform.gateway.application;

import com.riskplatform.common.error.BizException;
import com.riskplatform.gateway.domain.DecisionExecutionLogStore;
import com.riskplatform.gateway.domain.EngineDecisionRecordView;
import com.riskplatform.gateway.domain.EngineDecisionStatsView;
import com.riskplatform.gateway.domain.InvocationTraceView;

/**
 * 执行链路查询（XT1）：优先从 engine_decision_record 还原，与调用查询同源。
 */
public class InvocationTraceService {

    private final DecisionExecutionLogStore store;

    public InvocationTraceService(DecisionExecutionLogStore store) {
        this.store = store;
    }

    public InvocationTraceView queryByEventId(String eventId) {
        EngineDecisionRecordView engine = store.findEngineByEventId(eventId);
        if (engine == null) {
            throw BizException.notFound("事件链路不存在: " + eventId);
        }
        return InvocationTraceMapper.from(engine);
    }

    public EngineDecisionStatsView queryEngineStats(Long startTimeMs, Long endTimeMs, String eventTypeCode) {
        return store.queryEngineStats(startTimeMs, endTimeMs, eventTypeCode);
    }
}
