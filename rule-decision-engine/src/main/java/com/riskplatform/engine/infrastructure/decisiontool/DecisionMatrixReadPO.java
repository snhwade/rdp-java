package com.riskplatform.engine.infrastructure.decisiontool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** decision_matrix 只读 PO。 */
@TableName("decision_matrix")
public class DecisionMatrixReadPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String rowVar;
    private String rowBinsJson;
    private String colVar;
    private String colBinsJson;
    private String cellsJson;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
