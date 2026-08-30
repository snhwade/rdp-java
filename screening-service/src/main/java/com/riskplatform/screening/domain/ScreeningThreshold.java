package com.riskplatform.screening.domain;

/**
 * 名称匹配相似度阈值（R11.4）：取值范围 0.00–1.00，默认 0.85。
 */
public record ScreeningThreshold(double value) {

    public static final double MIN = 0.00;
    public static final double MAX = 1.00;
    public static final double DEFAULT = 0.85;

    public ScreeningThreshold {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException(
                    "相似度阈值须在 [" + MIN + "," + MAX + "]，实际为 " + value);
        }
    }

    public static ScreeningThreshold defaultThreshold() {
        return new ScreeningThreshold(DEFAULT);
    }
}
