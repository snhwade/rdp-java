package com.riskplatform.ruleconfig.infrastructure.rulev2;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * rule_dynamic_score 表持久化对象（V15）。
 *
 * <p>评分规则动态分区间子表，按指标取值区间配置动态得分（R4.2/R4.6）。
 */
@TableName("rule_dynamic_score")
public class RuleDynamicScorePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleV2Id;
    private String indicatorRefName;
    private BigDecimal lower;
    private BigDecimal upper;
    private Integer lowerInclusive;
    private Integer upperInclusive;
    private BigDecimal score;
    private Integer orderNo;

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

    public String getIndicatorRefName() {
        return indicatorRefName;
    }

    public void setIndicatorRefName(String indicatorRefName) {
        this.indicatorRefName = indicatorRefName;
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

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }
}
