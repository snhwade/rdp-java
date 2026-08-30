package com.riskplatform.engine.infrastructure.decisiontool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** decision_table 只读 PO（运行时加载决策工具定义）。 */
@TableName("decision_table")
public class DecisionTableReadPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String hitPolicy;
    private String rowsJson;
    private String status;

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

    public String getHitPolicy() {
        return hitPolicy;
    }

    public void setHitPolicy(String hitPolicy) {
        this.hitPolicy = hitPolicy;
    }

    public String getRowsJson() {
        return rowsJson;
    }

    public void setRowsJson(String rowsJson) {
        this.rowsJson = rowsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
