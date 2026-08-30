package com.riskplatform.ruleconfig.infrastructure.decisionflow;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 决策流持久化对象（对应 decision_flow 表，S4）。nodes/edges 以 JSON 文本存储。 */
@TableName("decision_flow")
public class DecisionFlowPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String eventTypeCode;
    private String nodesJson;
    private String edgesJson;
    private String startNodeId;
    private String status;
    private String remark;
    private Integer prevOnlineVersion;
    // 扩展阶段扩展列（见 V18__decision_flow_ext.sql）
    private String scenarioIdsJson;
    private String eventCodesJson;
    private Long applicableOrgId;
    private Boolean includeSubOrg;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
    }

    public String getNodesJson() {
        return nodesJson;
    }

    public void setNodesJson(String nodesJson) {
        this.nodesJson = nodesJson;
    }

    public String getEdgesJson() {
        return edgesJson;
    }

    public void setEdgesJson(String edgesJson) {
        this.edgesJson = edgesJson;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public void setStartNodeId(String startNodeId) {
        this.startNodeId = startNodeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getPrevOnlineVersion() {
        return prevOnlineVersion;
    }

    public void setPrevOnlineVersion(Integer prevOnlineVersion) {
        this.prevOnlineVersion = prevOnlineVersion;
    }

    public String getScenarioIdsJson() {
        return scenarioIdsJson;
    }

    public void setScenarioIdsJson(String scenarioIdsJson) {
        this.scenarioIdsJson = scenarioIdsJson;
    }

    public String getEventCodesJson() {
        return eventCodesJson;
    }

    public void setEventCodesJson(String eventCodesJson) {
        this.eventCodesJson = eventCodesJson;
    }

    public Long getApplicableOrgId() {
        return applicableOrgId;
    }

    public void setApplicableOrgId(Long applicableOrgId) {
        this.applicableOrgId = applicableOrgId;
    }

    public Boolean getIncludeSubOrg() {
        return includeSubOrg;
    }

    public void setIncludeSubOrg(Boolean includeSubOrg) {
        this.includeSubOrg = includeSubOrg;
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
