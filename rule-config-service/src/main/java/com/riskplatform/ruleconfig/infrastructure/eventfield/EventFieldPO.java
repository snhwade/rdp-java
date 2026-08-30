package com.riskplatform.ruleconfig.infrastructure.eventfield;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * event_field 表持久化对象（risk-console-redesign V20，R4.8）。
 *
 * <p>purposes_json 以 JSON 数组字符串存储（如 {@code ["COMPUTE","DECISION"]}），
 * 由仓储手动序列化/反序列化为枚举集合；derived 以 0/1 存储衍生字段标记。
 */
@TableName("event_field")
public class EventFieldPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventTypeCode;
    private Long fieldId;
    /** 事件字段用途多选，JSON 数组字符串 [COMPUTE,DECISION]。 */
    private String purposesJson;
    /** 是否衍生字段 0=否 1=是。 */
    private Integer derived;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public String getPurposesJson() {
        return purposesJson;
    }

    public void setPurposesJson(String purposesJson) {
        this.purposesJson = purposesJson;
    }

    public Integer getDerived() {
        return derived;
    }

    public void setDerived(Integer derived) {
        this.derived = derived;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
