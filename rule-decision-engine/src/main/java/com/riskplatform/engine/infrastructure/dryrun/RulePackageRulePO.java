package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 规则包-规则关联只读持久化对象（对应 rule_package_rule 表，V14，R5.2）。
 *
 * <p>试运行加载规则包时据此取出包内规则及包内优先级。仅读取，不修改。
 */
@TableName("rule_package_rule")
public class RulePackageRulePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rulePackageId;
    private Long ruleV2Id;
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
