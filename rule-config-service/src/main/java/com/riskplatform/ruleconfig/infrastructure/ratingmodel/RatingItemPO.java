package com.riskplatform.ruleconfig.infrastructure.ratingmodel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 评级子项/定级项持久化对象（对应 rating_item 表，见 V23）。
 *
 * <p>评分定级使用 category/subItem/conditionExpr/score/subItemCap/importance；
 * 直接定级使用 conditionExpr/grade。表字段 {@code condition_expr} 映射至 {@link #conditionExpr}。
 */
@TableName("rating_item")
public class RatingItemPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ratingModelId;
    private String category;
    private String subItem;
    @TableField("condition_expr")
    private String conditionExpr;
    private BigDecimal score;
    private BigDecimal subItemCap;
    private String importance;
    private String grade;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubItem() {
        return subItem;
    }

    public void setSubItem(String subItem) {
        this.subItem = subItem;
    }

    public String getConditionExpr() {
        return conditionExpr;
    }

    public void setConditionExpr(String conditionExpr) {
        this.conditionExpr = conditionExpr;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getSubItemCap() {
        return subItemCap;
    }

    public void setSubItemCap(BigDecimal subItemCap) {
        this.subItemCap = subItemCap;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }
}
