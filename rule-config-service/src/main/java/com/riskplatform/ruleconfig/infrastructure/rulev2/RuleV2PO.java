package com.riskplatform.ruleconfig.infrastructure.rulev2;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * rule_v2 表持久化对象（V15）。
 *
 * <p>审计列 create_time/update_time 由数据库默认值与 ON UPDATE 维护，
 * create_user/update_user 暂留空。列名 snake_case 由 MyBatis-Plus 默认下划线转驼峰映射。
 */
@TableName("rule_v2")
public class RuleV2PO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private Long rulePackageId;
    /** HIT / SCORE */
    private String ruleKind;
    private String eventTypeCode;
    private String riskLevelCode;
    private String riskTypeCode;
    private BigDecimal baseScore;
    private String conditionJson;
    private String compiledExpr;
    private Integer exprVersion;
    private Integer priority;
    private Integer shortCircuited;
    private Long applicableOrgId;
    private Integer includeSubOrg;
    private String remark;
    private Integer version;
    /** ONLINE / TRIAL_RUN / OFFLINE */
    private String status;
    private String createUser;
    private LocalDateTime createTime;
    private String updateUser;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getRulePackageId() {
        return rulePackageId;
    }

    public void setRulePackageId(Long rulePackageId) {
        this.rulePackageId = rulePackageId;
    }

    public String getRuleKind() {
        return ruleKind;
    }

    public void setRuleKind(String ruleKind) {
        this.ruleKind = ruleKind;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
    }

    public String getRiskLevelCode() {
        return riskLevelCode;
    }

    public void setRiskLevelCode(String riskLevelCode) {
        this.riskLevelCode = riskLevelCode;
    }

    public String getRiskTypeCode() {
        return riskTypeCode;
    }

    public void setRiskTypeCode(String riskTypeCode) {
        this.riskTypeCode = riskTypeCode;
    }

    public BigDecimal getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(BigDecimal baseScore) {
        this.baseScore = baseScore;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public String getCompiledExpr() {
        return compiledExpr;
    }

    public void setCompiledExpr(String compiledExpr) {
        this.compiledExpr = compiledExpr;
    }

    public Integer getExprVersion() {
        return exprVersion;
    }

    public void setExprVersion(Integer exprVersion) {
        this.exprVersion = exprVersion;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getShortCircuited() {
        return shortCircuited;
    }

    public void setShortCircuited(Integer shortCircuited) {
        this.shortCircuited = shortCircuited;
    }

    public Long getApplicableOrgId() {
        return applicableOrgId;
    }

    public void setApplicableOrgId(Long applicableOrgId) {
        this.applicableOrgId = applicableOrgId;
    }

    public Integer getIncludeSubOrg() {
        return includeSubOrg;
    }

    public void setIncludeSubOrg(Integer includeSubOrg) {
        this.includeSubOrg = includeSubOrg;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
