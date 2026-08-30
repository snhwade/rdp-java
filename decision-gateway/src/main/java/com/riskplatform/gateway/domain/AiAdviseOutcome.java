package com.riskplatform.gateway.domain;

/**
 * 同步 AI 推理结果（含超时/失败语义）。
 */
public record AiAdviseOutcome(
        boolean success,
        boolean timedOut,
        AiAdviseResult result,
        String failReason) {

    public static AiAdviseOutcome ok(AiAdviseResult result) {
        return new AiAdviseOutcome(true, false, result, null);
    }

    public static AiAdviseOutcome timedOut(String reason) {
        return new AiAdviseOutcome(false, true, null, reason);
    }

    public static AiAdviseOutcome failed(String reason) {
        return new AiAdviseOutcome(false, false, null, reason);
    }

    public String decisionOrNull() {
        return result == null ? null : result.decision();
    }
}
