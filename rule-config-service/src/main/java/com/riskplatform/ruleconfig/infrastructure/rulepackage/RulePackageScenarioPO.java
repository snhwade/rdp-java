package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * rule_package_scenario 表持久化对象（V14）：规则包-场景多对多关联。
 */
@TableName("rule_package_scenario")
public class RulePackageScenarioPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rulePackageId;
    private Long scenarioId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRulePackageId() {
        return rulePackageId;
    }

    public void setRulePackageId(Long rulePackageId) {
        this.rulePackageId = rulePackageId;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(Long scenarioId) {
        this.scenarioId = scenarioId;
    }
}
