package com.riskplatform.engine.domain.decision;

/**
 * 决策时限与超时处置配置（R6.5/R6.7）。
 *
 * @param timeoutMs           决策时限（毫秒），范围 1..5000，默认 500
 * @param timeoutDisposition  超时处置决策（如 REVIEW/PASS）
 */
public record DecisionConfig(int timeoutMs, Decision timeoutDisposition) {

    public static final int MIN_TIMEOUT_MS = 1;
    public static final int MAX_TIMEOUT_MS = 5000;
    public static final int DEFAULT_TIMEOUT_MS = 500;

    public DecisionConfig {
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                    "决策时限须在 [" + MIN_TIMEOUT_MS + "," + MAX_TIMEOUT_MS + "]，实际为 " + timeoutMs);
        }
        if (timeoutDisposition == null) {
            throw new IllegalArgumentException("超时处置决策不能为空");
        }
    }

    /** 默认配置：500ms 超时，超时转人工复核。 */
    public static DecisionConfig defaults() {
        return new DecisionConfig(DEFAULT_TIMEOUT_MS, Decision.REVIEW);
    }
}
