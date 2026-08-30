package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("logical_indicator_member")
public class LogicalIndicatorMemberPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long logicalId;
    private String memberRefName;
    private String eventTypeCode;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLogicalId() { return logicalId; }
    public void setLogicalId(Long logicalId) { this.logicalId = logicalId; }
    public String getMemberRefName() { return memberRefName; }
    public void setMemberRefName(String memberRefName) { this.memberRefName = memberRefName; }
    public String getEventTypeCode() { return eventTypeCode; }
    public void setEventTypeCode(String eventTypeCode) { this.eventTypeCode = eventTypeCode; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
