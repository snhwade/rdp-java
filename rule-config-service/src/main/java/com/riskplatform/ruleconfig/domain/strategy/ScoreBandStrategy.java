package com.riskplatform.ruleconfig.domain.strategy;

import com.riskplatform.common.error.ValidationException;

/**
 * 评分区间-策略绑定（R3，配合 R1.3/R1.6）。
 *
 * <p>将规则包评分模式的某个分值区间（rule_package_score_band）绑定到一个策略定义。
 * 评分模式按总分落入的区间映射输出策略（聚合在 5.3）。
 */
public class ScoreBandStrategy {

    private Long id;
    private Long scoreBandId;
    private Long strategyDefId;

    private ScoreBandStrategy() {
    }

    /** 工厂方法：创建评分区间-策略绑定。 */
    public static ScoreBandStrategy create(Long scoreBandId, Long strategyDefId) {
        ScoreBandStrategy s = new ScoreBandStrategy();
        s.scoreBandId = scoreBandId;
        s.strategyDefId = strategyDefId;
        s.validate();
        return s;
    }

    /** 从持久化重建（不重复校验）。 */
    public static ScoreBandStrategy rehydrate(Long id, Long scoreBandId, Long strategyDefId) {
        ScoreBandStrategy s = new ScoreBandStrategy();
        s.id = id;
        s.scoreBandId = scoreBandId;
        s.strategyDefId = strategyDefId;
        return s;
    }

    /** 校验绑定不变式。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (scoreBandId == null) {
            errors.field("scoreBandId", "必填");
        }
        if (strategyDefId == null) {
            errors.field("strategyDefId", "必填");
        }
        errors.throwIfAny();
    }

    public Long getId() {
        return id;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getScoreBandId() {
        return scoreBandId;
    }

    public Long getStrategyDefId() {
        return strategyDefId;
    }
}
