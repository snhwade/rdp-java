package com.riskplatform.engine.domain.rule;

import java.util.List;

/**
 * 规则组执行结果（R5）。
 *
 * @param hitDecisions  命中规则产生的决策（供决策聚合）
 * @param records       全部规则执行记录（含未命中/失败，R5.5）
 * @param status        规则组执行状态（R5.4）
 * @param fatalReason   致命错误原因（仅 INTERRUPTED 时有值）
 */
public record RuleExecutionResult(
        List<HitDecision> hitDecisions,
        List<RuleExecutionRecord> records,
        GroupExecutionStatus status,
        String fatalReason) {
}
