package com.riskplatform.rating.infrastructure;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;

/** merchant_rating 表持久化对象。 */
@TableName(value = "merchant_rating", autoResultMap = true)
public class MerchantRatingPO {
    @TableId
    private String merchantId;
    private Integer score;
    private String level;
    private String status;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Double> factors;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String m) { this.merchantId = m; }
    public Integer getScore() { return score; }
    public void setScore(Integer s) { this.score = s; }
    public String getLevel() { return level; }
    public void setLevel(String l) { this.level = l; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public Map<String, Double> getFactors() { return factors; }
    public void setFactors(Map<String, Double> f) { this.factors = f; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
