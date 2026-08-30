package com.riskplatform.engine.domain.decision;

import com.riskplatform.engine.domain.rule.HitDecision;
import com.riskplatform.engine.domain.rule.GroupExecutionStatus;

import java.util.List;

/**
 * 决策日志（R6.6/R15.1/R15.3）。
 *
 * <p>记录每次最终决策：事件标识、命中规则及各自决策与优先级、最终决策、耗时、
 * 超时原因、规则组执行状态（含 INTERRUPTED）。
 *
 * @param eventId       事件标识
 * @param finalDecision 最终决策
 * @param hitDecisions  参与聚合的全部命中规则及各自决策
 * @param elapsedMs     处理耗时
 * @param timeoutReason 超时原因（无则 null）
 * @param groupStatus   规则组执行状态
 */
public record DecisionLog(
        String eventId,
        Decision finalDecision,
        List<HitDecision> hitDecisions,
        long elapsedMs,
        String timeoutReason,
        GroupExecutionStatus groupStatus) {
}
