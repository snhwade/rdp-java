package com.riskplatform.ruleconfig.infrastructure.ratingmodel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/** 评级模型等级区间持久化对象（对应 rating_grade_band 表，见 V23）。 */
@TableName("rating_grade_band")
public class RatingGradeBandPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ratingModelId;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    private String grade;
    private Integer orderNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRatingModelId() {
        return ratingModelId;
    }

    public void setRatingModelId(Long ratingModelId) {
        this.ratingModelId = ratingModelId;
    }

    public BigDecimal getMinScore() {
        return minScore;
    }

    public void setMinScore(BigDecimal minScore) {
        this.minScore = minScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Integer getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }
}
