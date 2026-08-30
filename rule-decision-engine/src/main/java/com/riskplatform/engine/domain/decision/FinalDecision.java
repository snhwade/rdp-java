package com.riskplatform.engine.domain.decision;

/**
 * 最终决策结果（R6.6/R6.7）。
 *
 * @param decision      最终决策
 * @param timedOut      是否因超时按处置策略产出
 * @param timeoutReason 超时原因（无则 null）
 * @param elapsedMs     聚合耗时（毫秒）
 */
public record FinalDecision(Decision decision, boolean timedOut, String timeoutReason, long elapsedMs) {

    public static FinalDecision normal(Decision decision, long elapsedMs) {
        return new FinalDecision(decision, false, null, elapsedMs);
    }

    public static FinalDecision timeout(Decision disposition, String reason, long elapsedMs) {
        return new FinalDecision(disposition, true, reason, elapsedMs);
    }
}
