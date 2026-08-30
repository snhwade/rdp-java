package com.riskplatform.gateway.domain;

import java.util.Locale;

/**
 * AI 决策采纳模式（见 docs/enhancement-plan.md T1）。
 */
public enum AdoptionMode {

    /** 异步影子：不参与同步对外决策。 */
    SHADOW,
    /** 同步参考：AI 更严时最多抬升到 REVIEW，不可单独 REJECT。 */
    ADVISORY,
    /** 同步从严：strictest(引擎轨, AI)。 */
    STRICT,
    /** 同步覆盖：AI 成功则用 AI，否则回退引擎轨。 */
    OVERRIDE;

    public boolean requiresSyncAi() {
        return this != SHADOW;
    }

    public static AdoptionMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return SHADOW;
        }
        try {
            return AdoptionMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SHADOW;
        }
    }
}
