package com.riskplatform.engine.domain.decisionflow;

import com.riskplatform.engine.domain.decisionmatrix.DecisionMatrixDef;
import com.riskplatform.engine.domain.decisiontable.DecisionTableDef;
import com.riskplatform.engine.domain.decisiontree.DecisionTreeDef;
import com.riskplatform.engine.domain.scorecard.ScorecardDef;

import java.util.List;
import java.util.Map;

/**
 * 决策流定义（引擎执行侧，S4，扩展阶段扩展）。
 *
 * <p>节点 + 有向边 + 起始节点。决策表/评分卡节点引用的定义以内联映射提供
 * （decisionTables/scorecards：refId → 定义），避免引擎反查配置服务。
 *
 * <p><b>兼容策略</b>：保留旧节点类型 {@code DECISION_TABLE/SCORECARD}（历史决策流仍可执行），
 * 新增统一决策工具类型 {@code DECISION_TOOL} 与阶段二节点类型（规则包/模型/网关/分流/子流程）。
 * {@link Node} 新增 {@code refType}（引用资产类型），{@link Edge} 新增 {@code trafficPercent}
 * （冠军挑战流量比例）与 {@code isDefault}（默认边）；旧 JSON 缺失这些字段时反序列化为 null/false。
 *
 * @param nodes            节点列表
 * @param edges            边列表
 * @param startNodeId      起始节点 id
 * @param decisionTables   决策表定义映射（refId → 定义）
 * @param scorecards       评分卡定义映射（refId → 定义）
 * @param decisionTrees    决策树定义映射（refId → 定义，扩展阶段 9.3 决策工具节点用）
 * @param decisionMatrices 决策矩阵定义映射（refId → 定义，扩展阶段 9.3 决策工具节点用）
 */
public record DecisionFlowDef(
        List<Node> nodes,
        List<Edge> edges,
        String startNodeId,
        Map<Long, DecisionTableDef> decisionTables,
        Map<Long, ScorecardDef> scorecards,
        Map<Long, DecisionTreeDef> decisionTrees,
        Map<Long, DecisionMatrixDef> decisionMatrices) {

    /**
     * 兼容旧签名构造器（仅决策表/评分卡内联）。决策树/矩阵映射置空，
     * 既有 5 参调用点（如 {@code DecisionFlowEvalController}）无需改动即可继续编译运行。
     */
    public DecisionFlowDef(List<Node> nodes,
                           List<Edge> edges,
                           String startNodeId,
                           Map<Long, DecisionTableDef> decisionTables,
                           Map<Long, ScorecardDef> scorecards) {
        this(nodes, edges, startNodeId, decisionTables, scorecards, Map.of(), Map.of());
    }

    /**
     * 节点类型全集（扩展阶段）。旧类型 {@code DECISION_TABLE/SCORECARD} 保留兼容历史数据。
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
        /** @deprecated 旧版决策表节点，保留兼容；新建请用 {@link #DECISION_TOOL}。 */
        @Deprecated
        DECISION_TABLE,
        /** @deprecated 旧版评分卡节点，保留兼容；新建请用 {@link #DECISION_TOOL}。 */
        @Deprecated
        SCORECARD
    }

    /**
     * 节点：nodeId + 类型 + 引用资产类型 + 引用资产 id + 节点级配置。
     *
     * @param nodeId  节点标识
     * @param type    节点类型
     * @param refType 引用资产类型（如 DECISION_TABLE/SCORECARD/RULE_PACKAGE/MODEL/SUB_FLOW，可空）
     * @param refId   引用资产 id（可空）
     * @param config  节点级配置 JSON（可空）
     */
    public record Node(String nodeId, NodeType type, String refType, Long refId, String config) {
    }

    /**
     * 边：from→to + 条件 + 冠军挑战流量比例 + 默认边标识。
     *
     * @param from           起点
     * @param to             终点
     * @param condition      条件表达式（空表示无条件兜底边）
     * @param trafficPercent 冠军挑战分流流量百分比（仅冠军挑战出线用，可空）
     * @param isDefault      是否默认边（条件网关/子流程无匹配兜底）
     */
    public record Edge(String from, String to, String condition, Integer trafficPercent, boolean isDefault) {
    }
}
