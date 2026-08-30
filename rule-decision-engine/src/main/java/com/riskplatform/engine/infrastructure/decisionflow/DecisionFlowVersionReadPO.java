package com.riskplatform.engine.infrastructure.decisionflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** decision_flow_version 表只读 PO（加载 ONLINE 版本快照）。 */
@TableName("decision_flow_version")
public class DecisionFlowVersionReadPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long decisionFlowId;
    private Integer version;
    private String snapshotJson;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDecisionFlowId() {
        return decisionFlowId;
    }

    public void setDecisionFlowId(Long decisionFlowId) {
        this.decisionFlowId = decisionFlowId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
