package com.riskplatform.ruleconfig.domain.decisiontree;

import com.riskplatform.common.error.ValidationException;

import java.util.List;

/**
 * 决策树聚合根（S8）。
 *
 * <p>从根节点出发，内部节点按子分支条件下钻，叶子节点输出决策。
 *
 * <p>不变式：name/eventTypeCode/rootNodeId 必填；nodes 非空。
 */
public class DecisionTree {

    private Long id;
    private String name;
    private String eventTypeCode;
    private String rootNodeId;
    private List<Node> nodes;
    private String status;

    private DecisionTree() {
    }

    public static DecisionTree create(String name, String eventTypeCode, String rootNodeId, List<Node> nodes) {
        DecisionTree t = new DecisionTree();
        t.name = name;
        t.eventTypeCode = eventTypeCode;
        t.rootNodeId = rootNodeId;
        t.nodes = nodes;
        t.status = "ENABLED";
        t.validate();
        return t;
    }

    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        }
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            errors.field("eventTypeCode", "必填");
        }
        if (rootNodeId == null || rootNodeId.isBlank()) {
            errors.field("rootNodeId", "必填");
        }
        if (nodes == null || nodes.isEmpty()) {
            errors.field("nodes", "至少一个节点");
        }
        errors.throwIfAny();
    }

    public void update(String name, String rootNodeId, List<Node> nodes, String status) {
        this.name = name;
        this.rootNodeId = rootNodeId;
        this.nodes = nodes;
        this.status = status;
        validate();
    }

    /** 从持久化层重建（保留 status）。 */
    public static DecisionTree rehydrate(Long id, String name, String eventTypeCode, String rootNodeId,
                                         List<Node> nodes, String status) {
        DecisionTree t = new DecisionTree();
        t.id = id;
        t.name = name;
        t.eventTypeCode = eventTypeCode;
        t.rootNodeId = rootNodeId;
        t.nodes = nodes;
        t.status = status == null || status.isBlank() ? "ENABLED" : status;
        t.validate();
        return t;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public String getRootNodeId() {
        return rootNodeId;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public String getStatus() {
        return status;
    }

    /** 节点：内部节点有 children（条件→子节点），叶子节点 leaf=true 带决策。 */
    public record Node(String nodeId, boolean leaf, String decision, Integer priority, List<Branch> children) {
    }

    /** 分支：满足 condition 则进入 childNodeId。 */
    public record Branch(String condition, String childNodeId) {
    }
}
