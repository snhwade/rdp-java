package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "logical_indicator", autoResultMap = true)
public class LogicalIndicatorPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String refName;
    private String name;
    private String description;
    private String combineMode;
    private String combineExpression;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> dimensions;
    private Integer windowDays;
    private String sliceGranularity;
    private String defaultValueStrategy;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getRefName() { return refName; }
    public void setRefName(String refName) { this.refName = refName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCombineMode() { return combineMode; }
    public void setCombineMode(String combineMode) { this.combineMode = combineMode; }
    public String getCombineExpression() { return combineExpression; }
    public void setCombineExpression(String combineExpression) { this.combineExpression = combineExpression; }
    public List<String> getDimensions() { return dimensions; }
    public void setDimensions(List<String> dimensions) { this.dimensions = dimensions; }
    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; }
    public String getSliceGranularity() { return sliceGranularity; }
    public void setSliceGranularity(String sliceGranularity) { this.sliceGranularity = sliceGranularity; }
    public String getDefaultValueStrategy() { return defaultValueStrategy; }
    public void setDefaultValueStrategy(String defaultValueStrategy) { this.defaultValueStrategy = defaultValueStrategy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
