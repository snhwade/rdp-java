package com.riskplatform.engine.domain.decision;

/**
 * 决策结论（R6）。
 *
 * <p>严格性次序由严到宽：REJECT > REVIEW > PASS（R6.3）。
 */
public enum Decision {
    PASS(1),
    REVIEW(2),
    REJECT(3);

    private final int strictness;

    Decision(int strictness) {
        this.strictness = strictness;
    }

    /** 严格性数值，越大越严格。 */
    public int strictness() {
        return strictness;
    }
}
