package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 结构化规则只读持久化对象（对应 rule_v2 表，V15，R5.2）。
 *
 * <p>试运行加载目标定义时，从该表读取规则的编译表达式、决策语义所需字段（风险等级/基础分/短路/
 * 优先级）。仅读取，不修改。表由 rule-config-service Flyway V15 创建，引擎共享同一库。
 */
@TableName("rule_v2")
public class RuleV2SamplePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private Long rulePackageId;
    private String ruleKind;
    private String eventTypeCode;
    private String riskLevelCode;
    private String riskTypeCode;
    private BigDecimal baseScore;
    private String compiledExpr;
    private Integer priority;
    private Integer shortCircuited;
    private String status;

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

    public String getCompiledExpr() {
        return compiledExpr;
    }

    public void setCompiledExpr(String compiledExpr) {
        this.compiledExpr = compiledExpr;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
