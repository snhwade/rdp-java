package com.riskplatform.ruleconfig.infrastructure.decisionmatrix;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 决策矩阵持久化对象（对应 decision_matrix 表，S9）。bins/cells 以 JSON 文本存储。 */
@TableName("decision_matrix")
public class DecisionMatrixPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String eventTypeCode;
    private String rowVar;
    private String rowBinsJson;
    private String colVar;
    private String colBinsJson;
    private String cellsJson;
    private String status;
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

    public String getRowVar() {
        return rowVar;
    }

    public void setRowVar(String rowVar) {
        this.rowVar = rowVar;
    }

    public String getRowBinsJson() {
        return rowBinsJson;
    }

    public void setRowBinsJson(String rowBinsJson) {
        this.rowBinsJson = rowBinsJson;
    }

    public String getColVar() {
        return colVar;
    }

    public void setColVar(String colVar) {
        this.colVar = colVar;
    }

    public String getColBinsJson() {
        return colBinsJson;
    }

    public void setColBinsJson(String colBinsJson) {
        this.colBinsJson = colBinsJson;
    }

    public String getCellsJson() {
        return cellsJson;
    }

    public void setCellsJson(String cellsJson) {
        this.cellsJson = cellsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
