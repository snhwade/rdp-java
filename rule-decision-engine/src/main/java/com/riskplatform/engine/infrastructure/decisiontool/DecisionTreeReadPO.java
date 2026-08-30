package com.riskplatform.engine.infrastructure.decisiontool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** decision_tree 只读 PO。 */
@TableName("decision_tree")
public class DecisionTreeReadPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String rootNodeId;
    private String nodesJson;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRootNodeId() {
        return rootNodeId;
    }

    public void setRootNodeId(String rootNodeId) {
        this.rootNodeId = rootNodeId;
    }

    public String getNodesJson() {
        return nodesJson;
    }

    public void setNodesJson(String nodesJson) {
        this.nodesJson = nodesJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
