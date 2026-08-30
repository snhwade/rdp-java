package com.riskplatform.ruleconfig.infrastructure.eventtype;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * event_type 表持久化对象。
 *
 * <p>risk-console-redesign（V19）新增列：scenario_id、event_kind、purposes_json。
 * purposes_json 以 JSON 数组字符串存储（如 {@code ["COMPUTE","DECISION"]}），
 * 由仓储手动序列化/反序列化为枚举集合，避免泛型 TypeHandler 复杂度。
 */
@TableName("event_type")
public class EventTypePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    /** 0=禁用 1=启用 */
    private Integer status;
    /** 所属业务场景ID（V19）。 */
    private Long scenarioId;
    /** 事件类型分型 DIMENSION/FACT（V19）。 */
    private String eventKind;
    /** 事件用途多选，JSON 数组字符串 [COMPUTE,DECISION]（V19）。 */
    private String purposesJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(Long scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getEventKind() {
        return eventKind;
    }

    public void setEventKind(String eventKind) {
        this.eventKind = eventKind;
    }

    public String getPurposesJson() {
        return purposesJson;
    }

    public void setPurposesJson(String purposesJson) {
        this.purposesJson = purposesJson;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
