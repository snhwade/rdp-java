package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * rule_package_rule 表持久化对象（V14）：规则包-规则多对多关联（含包内优先级）。
 *
 * <p>支持同一规则归属多个规则包（R1.7）。
 */
@TableName("rule_package_rule")
public class RulePackageRulePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rulePackageId;
    private Long ruleV2Id;
    /** 包内规则优先级（数值越大优先级越高）。 */
    private Integer priority;

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

    public Long getRuleV2Id() {
        return ruleV2Id;
    }

    public void setRuleV2Id(Long ruleV2Id) {
        this.ruleV2Id = ruleV2Id;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
