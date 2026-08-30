package com.riskplatform.engine.infrastructure.decisionflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 决策流只读持久化对象（对应 decision_flow 表，S4 / 扩展阶段，R8.5）。
 *
 * <p>子决策流节点在线执行时按 flowId 读取被引用决策流的节点/边定义。仅读取 nodes_json /
 * edges_json / start_node_id / status，<strong>不修改任何配置</strong>（与
 * {@code DbRulePackageDefinitionAdapter} 同款只读 DAO 思路，参考 6.2/7.2）。
 *
 * <p>该表由 rule-config 拥有（见 V5__decision_flow.sql / V18__decision_flow_ext.sql），
 * 引擎共享同一库只读，故仅声明本任务需要的列，审计列与归属维度列（场景/机构）不在此映射。
 */
@TableName("decision_flow")
public class DecisionFlowReadPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String nodesJson;
    private String edgesJson;
    private String startNodeId;
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
}
