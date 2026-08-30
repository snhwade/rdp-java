package com.riskplatform.gateway.domain;

import com.riskplatform.common.model.PagedResult;

import java.util.Collection;
import java.util.Map;

/**
 * 引擎与 AI 决策执行记录持久化端口。
 */
public interface DecisionExecutionLogStore {

    void saveEngineRecord(
            String eventId,
            String correlationId,
            String businessOrderId,
            String merchantId,
            String eventTypeCode,
            long eventTimeMs,
            String engineDecision,
            String finalDecision,
            String invokeMode,
            Long rulePackageId,
            Long decisionFlowId,
            Map<String, Object> detail,
            Long elapsedMs);

    void createAiPending(
            String eventId,
            String correlationId,
            String businessOrderId,
            String merchantId,
            String eventTypeCode,
            long eventTimeMs,
            String engineDecision);

    void completeAiSuccess(
            String eventId,
            AiAdviseResult result,
            String engineDecision);

    void completeAiFailed(String eventId, String failReason);

    PagedResult<EngineDecisionRecordView> queryEngine(DecisionRecordQuery query);

    PagedResult<AiDecisionRecordView> queryAi(DecisionRecordQuery query);

    EngineDecisionRecordView findEngineByEventId(String eventId);

    AiDecisionRecordView findAiByEventId(String eventId);

    /** 按 eventId 批量加载 AI 记录（列表页关联摘要，空集合返回空 Map）。 */
    Map<String, AiDecisionRecordView> findAiByEventIds(Collection<String> eventIds);

    /** AI 分歧运营统计。 */
    AiDecisionStatsView queryAiStats(Long startTimeMs, Long endTimeMs, String eventTypeCode);

    /** 引擎调用时段统计（XS1）。 */
    EngineDecisionStatsView queryEngineStats(Long startTimeMs, Long endTimeMs, String eventTypeCode);
}
