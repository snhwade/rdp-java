package com.riskplatform.ruleconfig.domain.strategy;

import com.riskplatform.common.error.ValidationException;

/**
 * 规则-策略绑定（R3.1/R3.5）。
 *
 * <p>将一条结构化规则（rule_v2）绑定到一个策略定义（strategy_def）。
 * 验证策略（VERIFY）必须带整数优先级（数值越大优先级越高，R3.1），
 * 其余类别 priority 可为空；extraJson 透传绑定级附加参数。
 */
public class RuleStrategy {

    private Long id;
    private Long ruleV2Id;
    private Long strategyDefId;
    /** 优先级（验证策略用，数值越大优先级越高，R3.1）。 */
    private Integer priority;
    /** 绑定附加参数 JSON（可空）。 */
    private String extraJson;

    private RuleStrategy() {
    }

    /**
     * 工厂方法：创建规则-策略绑定。
     *
     * @param category 被绑定策略的类别，用于校验「验证策略必须带优先级」
     */
    public static RuleStrategy create(Long ruleV2Id, Long strategyDefId, StrategyCategory category,
                                      Integer priority, String extraJson) {
        RuleStrategy rs = new RuleStrategy();
        rs.ruleV2Id = ruleV2Id;
        rs.strategyDefId = strategyDefId;
        rs.priority = priority;
        rs.extraJson = extraJson;
        rs.validate(category);
        return rs;
    }

    /** 从持久化重建（不重复校验）。 */
    public static RuleStrategy rehydrate(Long id, Long ruleV2Id, Long strategyDefId,
                                         Integer priority, String extraJson) {
        RuleStrategy rs = new RuleStrategy();
        rs.id = id;
        rs.ruleV2Id = ruleV2Id;
        rs.strategyDefId = strategyDefId;
        rs.priority = priority;
        rs.extraJson = extraJson;
        return rs;
    }

    /** 校验绑定不变式。验证策略必须带优先级（R3.1）。 */
    public void validate(StrategyCategory category) {
        ValidationException.Builder errors = ValidationException.builder();
        if (ruleV2Id == null) {
            errors.field("ruleV2Id", "必填");
        }
        if (strategyDefId == null) {
            errors.field("strategyDefId", "必填");
        }
        if (category == StrategyCategory.VERIFY && priority == null) {
            errors.field("priority", "验证策略必须设置优先级");
        }
        errors.throwIfAny();
    }

    public Long getId() {
        return id;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getRuleV2Id() {
        return ruleV2Id;
    }

    public Long getStrategyDefId() {
        return strategyDefId;
    }

    public Integer getPriority() {
        return priority;
    }

    public String getExtraJson() {
        return extraJson;
    }
}
