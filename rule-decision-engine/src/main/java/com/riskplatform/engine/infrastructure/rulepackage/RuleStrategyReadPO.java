package com.riskplatform.engine.infrastructure.rulepackage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 规则-策略绑定只读持久化对象（对应 rule_strategy 表，V16，扩展阶段 R6.2）。
 *
 * <p>在线决策面规则包节点据此把命中规则绑定的策略并入决策流累计结果。仅读取，不修改。
 * priority 仅验证策略使用（数值越大优先级越高）。
 */
@TableName("rule_strategy")
public class RuleStrategyReadPO {

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
