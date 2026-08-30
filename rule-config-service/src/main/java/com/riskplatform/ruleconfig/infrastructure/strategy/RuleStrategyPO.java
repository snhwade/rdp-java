package com.riskplatform.ruleconfig.infrastructure.strategy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * rule_strategy 表持久化对象（V16）。规则-策略绑定。
 */
@TableName("rule_strategy")
public class RuleStrategyPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleV2Id;
    private Long strategyDefId;
    private Integer priority;
    private String extraJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRuleV2Id() {
        return ruleV2Id;
    }

    public void setRuleV2Id(Long ruleV2Id) {
        this.ruleV2Id = ruleV2Id;
    }

    public Long getStrategyDefId() {
        return strategyDefId;
    }

    public void setStrategyDefId(Long strategyDefId) {
        this.strategyDefId = strategyDefId;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }
}
