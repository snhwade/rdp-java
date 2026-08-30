package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * rule_package 表持久化对象（V14）。
 *
 * <p>审计列 create_time/update_time 由数据库默认值与 ON UPDATE 维护，
 * create_user/update_user 暂留空（与 V14 schema 一致）。列名 snake_case 由
 * MyBatis-Plus 默认下划线转驼峰映射。
 */
@TableName("rule_package")
public class RulePackagePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    /** HIT / SCORE */
    private String triggerMode;
    /** ONLINE / OFFLINE */
    private String computeMode;
    private String riskTypeCode;
    private Long ownerOrgId;
    private Long applicableOrgId;
    private Integer includeSubOrg;
    /** ENABLED / DISABLED */
    private String status;
    private Integer warnScoreEnabled;
    /** GTE / LT */
    private String warnScoreOp;
    private BigDecimal warnScoreThreshold;
    private Integer version;
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

    public String getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getComputeMode() {
        return computeMode;
    }

    public void setComputeMode(String computeMode) {
        this.computeMode = computeMode;
    }

    public String getRiskTypeCode() {
        return riskTypeCode;
    }

    public void setRiskTypeCode(String riskTypeCode) {
        this.riskTypeCode = riskTypeCode;
    }

    public Long getOwnerOrgId() {
        return ownerOrgId;
    }

    public void setOwnerOrgId(Long ownerOrgId) {
        this.ownerOrgId = ownerOrgId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getWarnScoreEnabled() {
        return warnScoreEnabled;
    }

    public void setWarnScoreEnabled(Integer warnScoreEnabled) {
        this.warnScoreEnabled = warnScoreEnabled;
    }

    public String getWarnScoreOp() {
        return warnScoreOp;
    }

    public void setWarnScoreOp(String warnScoreOp) {
        this.warnScoreOp = warnScoreOp;
    }

    public BigDecimal getWarnScoreThreshold() {
        return warnScoreThreshold;
    }

    public void setWarnScoreThreshold(BigDecimal warnScoreThreshold) {
        this.warnScoreThreshold = warnScoreThreshold;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
