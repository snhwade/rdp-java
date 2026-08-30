package com.riskplatform.engine.application;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rule.GroupExecutionStatus;
import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.List;

/**
 * 执行链路查询视图（R15.3/R15.4）。
 *
 * <p>按事件标识聚合一次事中决策的完整链路：规则匹配 → 规则执行（命中规则及各自决策/优先级）
 * → 决策聚合（最终决策、耗时、超时原因、规则组执行状态），由 traceId 关联 eventId。
 *
 * @param eventId       事件标识
 * @param traceId       链路追踪标识（与 eventId 关联，便于跨服务检索；无则回退 eventId）
 * @param finalDecision 最终决策
 * @param hitDecisions  参与聚合的命中规则及各自决策与决策优先级
 * @param elapsedMs     决策耗时（毫秒）
 * @param timeoutReason 超时原因（无则 null）
 * @param groupStatus   规则组执行状态（COMPLETED/INTERRUPTED）
 */
public record TraceView(
        String eventId,
        String traceId,
        Decision finalDecision,
        List<HitDecision> hitDecisions,
        long elapsedMs,
        String timeoutReason,
        GroupExecutionStatus groupStatus) {
}
