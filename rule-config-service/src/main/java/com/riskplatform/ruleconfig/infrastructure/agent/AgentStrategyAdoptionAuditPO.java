package com.riskplatform.ruleconfig.infrastructure.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_strategy_adoption_audit")
public class AgentStrategyAdoptionAuditPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long strategyId;
    private String strategyCode;
    private String fromMode;
    private String toMode;
    private String changedBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
    public String getStrategyCode() { return strategyCode; }
    public void setStrategyCode(String strategyCode) { this.strategyCode = strategyCode; }
    public String getFromMode() { return fromMode; }
    public void setFromMode(String fromMode) { this.fromMode = fromMode; }
    public String getToMode() { return toMode; }
    public void setToMode(String toMode) { this.toMode = toMode; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
