package com.riskplatform.ruleconfig.domain.decisionflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 决策流聚合根（S4，扩展阶段扩展）。
 *
 * <p>一个决策流 = 节点集合 + 有向边集合 + 起始节点。节点可为起止/名单检查/规则包/模型/决策工具/
 * 各类网关/冠军挑战/子决策流；边可带条件（满足才走），并支持冠军挑战流量比例与默认边标识。
 * 从起始节点遍历执行，到 END 汇总命中决策做聚合。
 *
 * <p>不变式（基础，加载/保存均校验）：name/eventTypeCode 必填；nodes/edges 非空；startNodeId
 * 必填且存在于节点中；每个节点 nodeId/type 必填。
 *
 * <p>保存期结构校验（仅 {@link #validateStructure()}，由应用服务在创建/更新时调用，
 * <b>不在仓储加载既有数据时执行</b>，以兼容历史数据）：有且仅有一个 START、至少一个 END（R7.5）。
 */
public class DecisionFlow {

    /**
     * 节点类型全集（扩展阶段）。
     *
     * <p><b>兼容策略</b>：历史决策流（S4）使用 {@code START/LIST_CHECK/DECISION_TABLE/SCORECARD/END}。
     * 为兼容既有数据，保留 {@code DECISION_TABLE/SCORECARD} 两个旧类型（历史 JSON 仍可反序列化与执行），
     * 同时新增统一的决策工具类型 {@code DECISION_TOOL}（决策表/评分卡/决策树/决策矩阵）及阶段二新增的
     * 规则包、模型、条件网关、并行网关、冠军挑战、子决策流类型。新建决策流推荐使用 {@code DECISION_TOOL}。
     */
    public enum NodeType {
        START,
        END,
        RULE_PACKAGE,
        MODEL,
        DECISION_TOOL,
        LIST_CHECK,
        CONDITION_GATEWAY,
        PARALLEL_GATEWAY,
        CHAMPION_CHALLENGER,
        SUB_FLOW,
        /** @deprecated 旧版决策表节点，保留以兼容既有数据；新建请用 {@link #DECISION_TOOL}。 */
        @Deprecated
        DECISION_TABLE,
        /** @deprecated 旧版评分卡节点，保留以兼容既有数据；新建请用 {@link #DECISION_TOOL}。 */
        @Deprecated
        SCORECARD
    }

    /**
     * 结束节点配置 JSON 中承载决策结果的键（与引擎 {@code EndDecisionResolver}、配置侧/前端约定一致）。
     */
    public static final String END_DECISION_CONFIG_KEY = "endDecision";

    /**
     * 结束节点合法的决策结果取值集合（R9.6）：退款 / 人工审核 / 自动通过 / 自动拒绝。
     */
    public static final Set<String> END_DECISIONS =
            Set.of("REFUND", "MANUAL_REVIEW", "AUTO_PASS", "AUTO_REJECT");

    /** 仅用于解析结束节点配置 JSON 的共享 ObjectMapper（无状态、线程安全）。 */
    private static final ObjectMapper CONFIG_MAPPER = new ObjectMapper();

    private Long id;
    private String name;
    private String eventTypeCode;
    private List<Node> nodes;
    private List<Edge> edges;
    private String startNodeId;
    private String status;
    /** 列表接口附加：卡片墙上下线状态（ONLINE/OFFLINE），非持久化字段。 */
    private String cardStatus;
    /** 备注（人工说明，D1）。 */
    private String remark;
    /** 回退用：上一启用版本号（R1，可空）。 */
    private Integer prevOnlineVersion;

    // 扩展阶段扩展：场景 / 多事件 / 适用机构（映射 V18 新增列）
    private List<Long> scenarioIds;
    private List<String> eventCodes;
    private Long applicableOrgId;
    private boolean includeSubOrg;

    private DecisionFlow() {
    }

    public static DecisionFlow create(String name, String eventTypeCode, List<Node> nodes,
                                      List<Edge> edges, String startNodeId) {
        DecisionFlow f = new DecisionFlow();
        f.name = name;
        f.eventTypeCode = eventTypeCode;
        f.nodes = nodes;
        f.edges = edges;
        f.startNodeId = startNodeId;
        f.status = "ENABLED";
        f.validate();
        return f;
    }

    /** 基础不变式校验（加载与保存均执行）。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        }
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            errors.field("eventTypeCode", "必填");
        }
        if (nodes == null || nodes.isEmpty()) {
            errors.field("nodes", "至少一个节点");
        }
        if (edges == null || edges.isEmpty()) {
            errors.field("edges", "至少一条边");
        }
        if (startNodeId == null || startNodeId.isBlank()) {
            errors.field("startNodeId", "必填");
        } else if (nodes != null && nodes.stream().noneMatch(n -> startNodeId.equals(n.nodeId()))) {
            errors.field("startNodeId", "起始节点不存在于节点列表");
        }
        errors.throwIfAny();
    }

    /**
     * 保存期结构校验（R7.5 / R9.5 / R9.6）：有且仅有一个 START、至少一个 END；条件网关每条出线均有条件或存在
     * 唯一默认线；并行网关成对（分叉数=汇聚数）；无非法环路；从 START 可达的每条路径都必须终止于 END
     * 节点（R9.5）；每个 END 节点都须配置合法的决策结果（R9.6）。
     *
     * <p>仅在应用服务创建/更新时调用；仓储加载既有数据时不调用，以兼容历史决策流结构。
     */
    public void validateStructure() {
        ValidationException.Builder errors = ValidationException.builder();
        if (nodes != null) {
            long startCount = nodes.stream().filter(n -> n.type() == NodeType.START).count();
            long endCount = nodes.stream().filter(n -> n.type() == NodeType.END).count();
            if (startCount != 1) {
                errors.field("nodes", "必须有且仅有一个开始(START)节点，当前=" + startCount);
            }
            if (endCount < 1) {
                errors.field("nodes", "至少需要一个结束(END)节点");
            }
            validateConditionGateways(errors);
            validateParallelGateways(errors);
            validateChampionChallengers(errors);
            validateNoCycle(errors);
            validateAllPathsReachEnd(errors);
            validateEndDecisions(errors);
        }
        errors.throwIfAny();
    }

    /**
     * 条件网关校验（R7.1/7.2）：每个条件网关至少有 2 条出线；每条出线均须带条件，
     * 或存在唯一的默认线（{@code isDefault=true}）作为兜底；默认线不可多于一条。
     */
    private void validateConditionGateways(ValidationException.Builder errors) {
        for (Node n : nodes) {
            if (n.type() != NodeType.CONDITION_GATEWAY) {
                continue;
            }
            List<Edge> outs = outgoing(n.nodeId());
            if (outs.size() < 2) {
                errors.field("edges", "条件网关[" + n.nodeId() + "]至少需要 2 条出线");
            }
            long defaultCount = outs.stream().filter(Edge::isDefault).count();
            if (defaultCount > 1) {
                errors.field("edges", "条件网关[" + n.nodeId() + "]默认线不可多于一条");
            }
            for (Edge e : outs) {
                boolean hasCondition = e.condition() != null && !e.condition().isBlank();
                if (!e.isDefault() && !hasCondition) {
                    errors.field("edges",
                            "条件网关[" + n.nodeId() + "]出线[" + e.to() + "]缺少条件且非默认线");
                }
            }
        }
    }

    /**
     * 并行网关成对校验（R7.3）：以图的度数推断——出度&gt;1 者为分叉网关、入度&gt;1 者为汇聚网关；
     * 分叉数须等于汇聚数（成对出现）。出度=入度=1 的并行网关视为非法（既非分叉也非汇聚）。
     */
    private void validateParallelGateways(ValidationException.Builder errors) {
        long forkCount = 0;
        long joinCount = 0;
        for (Node n : nodes) {
            if (n.type() != NodeType.PARALLEL_GATEWAY) {
                continue;
            }
            int out = outgoing(n.nodeId()).size();
            int in = incoming(n.nodeId()).size();
            boolean isFork = out > 1;
            boolean isJoin = in > 1;
            if (isFork) {
                forkCount++;
            }
            if (isJoin) {
                joinCount++;
            }
            if (!isFork && !isJoin) {
                errors.field("edges",
                        "并行网关[" + n.nodeId() + "]既非分叉(出度>1)也非汇聚(入度>1)，配置非法");
            }
        }
        if (forkCount != joinCount) {
            errors.field("nodes",
                    "并行网关须成对出现：分叉数=" + forkCount + "，汇聚数=" + joinCount);
        }
    }

    /**
     * 冠军挑战（分流）节点校验（R8.1）：每个冠军挑战节点至少有 2 条出线；每条出线须配置流量百分比
     * （{@code trafficPercent} 非空且为正）；所有出线的流量百分比之和须 = 100，否则保存校验拒绝。
     */
    private void validateChampionChallengers(ValidationException.Builder errors) {
        for (Node n : nodes) {
            if (n.type() != NodeType.CHAMPION_CHALLENGER) {
                continue;
            }
            List<Edge> outs = outgoing(n.nodeId());
            if (outs.size() < 2) {
                errors.field("edges", "冠军挑战节点[" + n.nodeId() + "]至少需要 2 条出线");
            }
            int sum = 0;
            for (Edge e : outs) {
                Integer p = e.trafficPercent();
                if (p == null || p <= 0) {
                    errors.field("edges",
                            "冠军挑战节点[" + n.nodeId() + "]出线[" + e.to() + "]缺少有效流量百分比(trafficPercent>0)");
                } else {
                    sum += p;
                }
            }
            if (!outs.isEmpty() && sum != 100) {
                errors.field("edges",
                        "冠军挑战节点[" + n.nodeId() + "]所有出线流量百分比之和须=100，当前=" + sum);
            }
        }
    }

    /**
     * 可达路径终止性校验（R9.5）：从 START 出发，凡可达节点都必须能够"走到"END。若存在一个可达节点
     * 不是 END 却没有任何出线（悬挂的死端），则存在一条不以 END 终止的可达路径，拒绝保存。
     *
     * <p>说明：本图为有向无环（环路由 {@link #validateNoCycle} 单独拒绝）。在无环前提下，"每条可达路径
     * 终止于 END" 等价于 "每个可达的非 END 节点至少有一条出线"——因为无环图的任意路径最终都会到达某个
     * 出度为 0 的节点，而出度为 0 的节点只允许是 END。该判定对存在环的情况不产生误判（环路另行拒绝）。
     */
    private void validateAllPathsReachEnd(ValidationException.Builder errors) {
        if (startNodeId == null || nodes == null) {
            return;
        }
        Map<String, Node> nodeById = new java.util.HashMap<>();
        for (Node n : nodes) {
            if (n.nodeId() != null) {
                nodeById.put(n.nodeId(), n);
            }
        }
        Set<String> reachable = new java.util.HashSet<>();
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        stack.push(startNodeId);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!reachable.add(current)) {
                continue;
            }
            for (Edge e : outgoing(current)) {
                if (e.to() != null && !reachable.contains(e.to())) {
                    stack.push(e.to());
                }
            }
        }
        for (String nodeId : reachable) {
            Node node = nodeById.get(nodeId);
            if (node == null) {
                continue;
            }
            if (node.type() == NodeType.END) {
                continue;
            }
            if (outgoing(nodeId).isEmpty()) {
                errors.field("edges",
                        "存在未到达结束(END)节点的可达路径：节点[" + nodeId + "]无后继且非结束节点");
            }
        }
    }

    /**
     * 结束节点决策结果必填校验（R9.6）：每个 END 节点的配置 JSON 必须包含合法的决策结果
     * （键 {@code endDecision}，取值 REFUND/MANUAL_REVIEW/AUTO_PASS/AUTO_REJECT 之一）；
     * 缺失或取值非法则拒绝保存并返回该节点对应的字段级错误。
     */
    private void validateEndDecisions(ValidationException.Builder errors) {
        for (Node n : nodes) {
            if (n.type() != NodeType.END) {
                continue;
            }
            String decision = parseEndDecision(n.config());
            if (decision == null || decision.isBlank()) {
                errors.field("nodes",
                        "结束(END)节点[" + n.nodeId() + "]必须配置决策结果(endDecision)");
            } else if (!END_DECISIONS.contains(decision)) {
                errors.field("nodes",
                        "结束(END)节点[" + n.nodeId() + "]决策结果非法[" + decision
                                + "]，取值须为 REFUND/MANUAL_REVIEW/AUTO_PASS/AUTO_REJECT 之一");
            }
        }
    }

    /**
     * 从 END 节点配置 JSON 中解析其决策结果（键 {@code endDecision}）。
     *
     * @param config 节点配置 JSON（可空）
     * @return 决策结果字符串（去首尾空白）；配置为空、非法 JSON 或未含该键时返回 {@code null}
     */
    private static String parseEndDecision(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> parsed = CONFIG_MAPPER.readValue(config, Map.class);
            Object value = parsed == null ? null : parsed.get(END_DECISION_CONFIG_KEY);
            return value == null ? null : String.valueOf(value).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** 非法环路校验（R7.5）：以 DFS 检测有向图是否存在回边（环），存在则拒绝。 */
    private void validateNoCycle(ValidationException.Builder errors) {        if (edges == null || startNodeId == null) {
            return;
        }
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> inStack = new java.util.HashSet<>();
        if (hasCycle(startNodeId, visited, inStack)) {
            errors.field("edges", "决策流存在非法环路");
        }
    }

    private boolean hasCycle(String nodeId, java.util.Set<String> visited, java.util.Set<String> inStack) {
        if (inStack.contains(nodeId)) {
            return true; // 回边：发现环
        }
        if (visited.contains(nodeId)) {
            return false;
        }
        visited.add(nodeId);
        inStack.add(nodeId);
        for (Edge e : outgoing(nodeId)) {
            if (hasCycle(e.to(), visited, inStack)) {
                return true;
            }
        }
        inStack.remove(nodeId);
        return false;
    }

    private List<Edge> outgoing(String nodeId) {
        if (edges == null) {
            return List.of();
        }
        return edges.stream().filter(e -> nodeId.equals(e.from())).toList();
    }

    private List<Edge> incoming(String nodeId) {
        if (edges == null) {
            return List.of();
        }
        return edges.stream().filter(e -> nodeId.equals(e.to())).toList();
    }

    public void update(String name, List<Node> nodes, List<Edge> edges, String startNodeId, String status) {
        this.name = name;
        this.nodes = nodes;
        this.edges = edges;
        this.startNodeId = startNodeId;
        this.status = status;
        validate();
    }

    /** 设置扩展阶段归属维度（场景/多事件/适用机构）。 */
    public void assignScope(List<Long> scenarioIds, List<String> eventCodes,
                            Long applicableOrgId, boolean includeSubOrg) {
        this.scenarioIds = scenarioIds;
        this.eventCodes = eventCodes;
        this.applicableOrgId = applicableOrgId;
        this.includeSubOrg = includeSubOrg;
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

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public String getStatus() {
        return status;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getCardStatus() {
        return cardStatus;
    }

    public void setCardStatus(String cardStatus) {
        this.cardStatus = cardStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void assignRemark(String remark) {
        this.remark = remark;
    }

    public Integer getPrevOnlineVersion() {
        return prevOnlineVersion;
    }

    public void assignPrevOnlineVersion(Integer prevOnlineVersion) {
        this.prevOnlineVersion = prevOnlineVersion;
    }

    public List<Long> getScenarioIds() {
        return scenarioIds;
    }

    public List<String> getEventCodes() {
        return eventCodes;
    }

    public Long getApplicableOrgId() {
        return applicableOrgId;
    }

    public boolean isIncludeSubOrg() {
        return includeSubOrg;
    }

    /**
     * 节点：nodeId + 类型 + 引用资产类型 + 引用资产 id + 节点级配置（JSON）。
     *
     * @param nodeId  节点标识
     * @param type    节点类型
     * @param refType 引用资产类型（如 RULE_PACKAGE/DECISION_TABLE/SCORECARD/MODEL/SUB_FLOW 等，可空）
     * @param refId   引用资产 id（决策表/评分卡/规则包/子流程等，可空）
     * @param config  节点级配置 JSON（如名单维度、网关/分流/降级配置，可空）
     */
    public record Node(String nodeId, NodeType type, String refType, Long refId, String config) {
    }

    /**
     * 边：from→to + 条件（Aviator 表达式，空表示无条件兜底边）+ 冠军挑战流量比例 + 默认边标识。
     *
     * @param from           起点节点 id
     * @param to             终点节点 id
     * @param condition      条件表达式（可空）
     * @param trafficPercent 冠军挑战分流流量百分比（仅冠军挑战出线用，可空）
     * @param isDefault      是否默认边（条件网关/子流程无匹配时的兜底边）
     */
    public record Edge(String from, String to, String condition, Integer trafficPercent,
                       @JsonProperty("isDefault") @JsonAlias("default") boolean isDefault) {
    }
}
