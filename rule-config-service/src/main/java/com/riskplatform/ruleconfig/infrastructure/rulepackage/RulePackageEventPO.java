package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * rule_package_event 表持久化对象（V14）：规则包-决策事件多对多关联。
 */
@TableName("rule_package_event")
public class RulePackageEventPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rulePackageId;
    private String eventTypeCode;

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

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
    }
}
