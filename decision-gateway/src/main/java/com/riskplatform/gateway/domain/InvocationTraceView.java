package com.riskplatform.gateway.domain;

import java.util.List;
import java.util.Map;

/**
 * 执行链路查询视图（XT1）：从 engine_decision_record.detail_json 还原。
 */
public record InvocationTraceView(
        String eventId,
        String traceId,
        String finalDecision,
        List<HitDecisionView> hitDecisions,
        long elapsedMs,
        String timeoutReason,
        String groupStatus,
        Map<String, Object> selectorMatch,
        List<RuleExecutionView> ruleExecutions,
        List<String> flowPath,
        List<FlowTraceStepView> flowTrace) {

    public record HitDecisionView(long ruleId, int priority, String decision, boolean trialRun) {
    }

    public record RuleExecutionView(
            long ruleId,
            int version,
            boolean hit,
            boolean failed,
            String failReason) {
    }

    public record FlowTraceStepView(
            String nodeId,
            String nodeType,
            String refType,
            Long refId,
            List<HitDecisionView> hits,
            Map<String, Object> assignments) {
    }
}
