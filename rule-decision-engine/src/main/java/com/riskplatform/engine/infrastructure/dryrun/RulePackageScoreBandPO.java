package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 规则包评分分值区间只读持久化对象（对应 rule_package_score_band 表，V14，R5.4）。
 *
 * <p>试运行评分模式按分值区间映射风险等级。仅读取，不修改。
 */
@TableName("rule_package_score_band")
public class RulePackageScoreBandPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rulePackageId;
    private BigDecimal lower;
    private BigDecimal upper;
    private Integer lowerInclusive;
    private Integer upperInclusive;
    private String riskLevelCode;
    private Integer orderNo;

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

    public BigDecimal getLower() {
        return lower;
    }

    public void setLower(BigDecimal lower) {
        this.lower = lower;
    }

    public BigDecimal getUpper() {
        return upper;
    }

    public void setUpper(BigDecimal upper) {
        this.upper = upper;
    }

    public Integer getLowerInclusive() {
        return lowerInclusive;
    }

    public void setLowerInclusive(Integer lowerInclusive) {
        this.lowerInclusive = lowerInclusive;
    }

    public Integer getUpperInclusive() {
        return upperInclusive;
    }

    public void setUpperInclusive(Integer upperInclusive) {
        this.upperInclusive = upperInclusive;
    }

    public String getRiskLevelCode() {
        return riskLevelCode;
    }

    public void setRiskLevelCode(String riskLevelCode) {
        this.riskLevelCode = riskLevelCode;
    }

    public Integer getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }
}
