package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * rule_package_score_band 表持久化对象（V14）：评分模式分值区间。
 */
@TableName("rule_package_score_band")
public class RulePackageScoreBandPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rulePackageId;
    /** 区间下界（可为负，空表示负无穷）。 */
    private BigDecimal lower;
    /** 区间上界（空表示正无穷）。 */
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
