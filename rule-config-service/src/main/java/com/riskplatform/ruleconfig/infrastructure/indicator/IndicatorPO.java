package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** indicator_definition 表持久化对象。dimensions / event_type_codes 以 JSON 存储。 */
@TableName(value = "indicator_definition", autoResultMap = true)
public class IndicatorPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String refName;
    private String name;
    private String description;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> eventTypeCodes;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> dimensions;
    private Integer windowDays;
    private String sliceGranularity;
    private String accScript;
    private String defaultValueStrategy;
    private String status;
    private String templateType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> templateConfig;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getRefName() { return refName; }
    public void setRefName(String r) { this.refName = r; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public List<String> getEventTypeCodes() { return eventTypeCodes; }
    public void setEventTypeCodes(List<String> e) { this.eventTypeCodes = e; }
    public List<String> getDimensions() { return dimensions; }
    public void setDimensions(List<String> d) { this.dimensions = d; }
    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer w) { this.windowDays = w; }
    public String getSliceGranularity() { return sliceGranularity; }
    public void setSliceGranularity(String s) { this.sliceGranularity = s; }
    public String getAccScript() { return accScript; }
    public void setAccScript(String a) { this.accScript = a; }
    public String getDefaultValueStrategy() { return defaultValueStrategy; }
    public void setDefaultValueStrategy(String d) { this.defaultValueStrategy = d; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String t) { this.templateType = t; }
    public Map<String, Object> getTemplateConfig() { return templateConfig; }
    public void setTemplateConfig(Map<String, Object> c) { this.templateConfig = c; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
