package com.riskplatform.engine.domain.rating;

import java.math.BigDecimal;

/**
 * 等级区间（引擎执行侧，评级模型共用值对象）。
 *
 * <p>描述评级模型一个等级所覆盖的分值区间 [{@link #minScore}, {@link #maxScore}] 及其等级名
 * {@link #grade}。配置侧（rule-config）在保存时已校验同一模型各区间互不重叠且连续覆盖分值范围
 * （R11.3/R11.4），故执行侧只读不再二次校验覆盖性。
 *
 * <p>本类型为评分定级（{@link ScoreBasedGrader}）与直接定级（DirectGrader）共用：评分定级用其做
 * 总分到等级的区间映射；直接定级用其建立统一的等级序（见 {@link GradeOrder}）。
 *
 * @param minScore 区间下界（含）
 * @param maxScore 区间上界（含）
 * @param grade    等级名（如 "一级"/"A" 等，由配置侧定义）
 */
public record GradeBand(BigDecimal minScore, BigDecimal maxScore, String grade) {

    public GradeBand {
        if (minScore == null || maxScore == null) {
            throw new IllegalArgumentException("GradeBand min/max score must not be null");
        }
        if (minScore.compareTo(maxScore) > 0) {
            throw new IllegalArgumentException("GradeBand minScore must be <= maxScore");
        }
    }

    /**
     * 判断分值是否落入本区间（左闭右闭 [min, max]）。
     *
     * <p>相邻区间在共享边界处会同时命中，调用方（{@link ScoreBasedGrader}）按区间升序取首个命中者，
     * 以保证边界归入的确定性。
     *
     * @param score 待判定分值
     * @return 落入返回 true
     */
    public boolean contains(BigDecimal score) {
        return score != null
                && score.compareTo(minScore) >= 0
                && score.compareTo(maxScore) <= 0;
    }
}
