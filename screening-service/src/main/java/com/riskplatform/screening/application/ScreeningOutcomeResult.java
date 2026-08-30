package com.riskplatform.screening.application;

import com.riskplatform.screening.domain.ScreeningOutcome;
import com.riskplatform.screening.domain.ScreeningResult;

/**
 * 筛查处置结果（R11）：在领域筛查结果之上附加超时/失败状态，供决策引擎按策略处置。
 *
 * @param outcome 处置状态
 * @param result  领域筛查结果（TIMEOUT/FAILED 时可能为 null）
 * @param reason  超时/失败原因（无则 null）
 */
public record ScreeningOutcomeResult(ScreeningOutcome outcome, ScreeningResult result, String reason) {

    public static ScreeningOutcomeResult fromResult(ScreeningResult r) {
        return new ScreeningOutcomeResult(r.hit() ? ScreeningOutcome.HIT : ScreeningOutcome.MISS, r, null);
    }

    public static ScreeningOutcomeResult timeout(String reason) {
        return new ScreeningOutcomeResult(ScreeningOutcome.TIMEOUT, null, reason);
    }

    public static ScreeningOutcomeResult failed(String reason) {
        return new ScreeningOutcomeResult(ScreeningOutcome.FAILED, null, reason);
    }
}
