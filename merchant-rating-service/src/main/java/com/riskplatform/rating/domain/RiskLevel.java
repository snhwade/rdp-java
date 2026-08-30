package com.riskplatform.rating.domain;

/**
 * 商户风险等级（R12.2）。
 *
 * <p>五档区间互不重叠且完全覆盖 [0,100]：
 * <ul>
 *   <li>LOW 低：0–20</li>
 *   <li>MID_LOW 中低：21–40</li>
 *   <li>MID 中：41–60</li>
 *   <li>MID_HIGH 中高：61–80</li>
 *   <li>HIGH 高：81–100</li>
 * </ul>
 */
public enum RiskLevel {
    LOW(0, 20, "低"),
    MID_LOW(21, 40, "中低"),
    MID(41, 60, "中"),
    MID_HIGH(61, 80, "中高"),
    HIGH(81, 100, "高");

    private final int minInclusive;
    private final int maxInclusive;
    private final String display;

    RiskLevel(int minInclusive, int maxInclusive, String display) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.display = display;
    }

    public int getMinInclusive() {
        return minInclusive;
    }

    public int getMaxInclusive() {
        return maxInclusive;
    }

    public String getDisplay() {
        return display;
    }

    /**
     * 将 0..100 的评分映射到对应风险等级。
     *
     * @param score 风险评分，必须在 [0,100]
     * @return 对应风险等级（区间不重叠、全覆盖）
     * @throws IllegalArgumentException 评分越界
     */
    public static RiskLevel fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("风险评分必须在 [0,100]，实际为 " + score);
        }
        for (RiskLevel level : values()) {
            if (score >= level.minInclusive && score <= level.maxInclusive) {
                return level;
            }
        }
        // 理论不可达（区间已全覆盖）
        throw new IllegalStateException("评分未匹配到任何风险等级: " + score);
    }
}
