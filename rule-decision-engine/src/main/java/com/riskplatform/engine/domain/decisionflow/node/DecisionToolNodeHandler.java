package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.asset.AssetRef;
import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.decisionmatrix.DecisionMatrixDef;
import com.riskplatform.engine.domain.decisionmatrix.DecisionMatrixEvaluator;
import com.riskplatform.engine.domain.decisiontable.DecisionTableDef;
import com.riskplatform.engine.domain.decisiontable.DecisionTableEvaluator;
import com.riskplatform.engine.domain.decisiontree.DecisionTreeDef;
import com.riskplatform.engine.domain.decisiontree.DecisionTreeEvaluator;
import com.riskplatform.engine.domain.rule.HitDecision;
import com.riskplatform.engine.domain.scorecard.ScorecardDef;
import com.riskplatform.engine.domain.scorecard.ScorecardEvaluator;
import com.riskplatform.engine.domain.scorecard.ScorecardResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 决策工具节点处理器（DECISION_TOOL，扩展阶段，R6.3）。
 *
 * <p>复用既有四类决策工具执行器，覆盖 R6.3 要求的「决策表/评分卡/决策树/决策矩阵」：
 * <ul>
 *   <li>{@link DecisionTableEvaluator}（S2 决策表）；</li>
 *   <li>{@link ScorecardEvaluator}（S3 评分卡）；</li>
 *   <li>{@link DecisionTreeEvaluator}（S8 决策树，9.3 接入）；</li>
 *   <li>{@link DecisionMatrixEvaluator}（S9 决策矩阵，9.3 接入）。</li>
 * </ul>
 * 工具定义以内联映射提供（{@link DecisionFlowDef#decisionTables()/scorecards()/decisionTrees()/
 * decisionMatrices()}：refId → 定义），避免引擎反查配置服务，逻辑与旧
 * {@link com.riskplatform.engine.domain.decisionflow.DecisionFlowEvaluator} 中的
 * DECISION_TABLE/SCORECARD 分支一致，保证兼容。
 *
 * <p><b>兼容策略</b>：本处理器可被注册为统一类型 {@code DECISION_TOOL}，也可被注册为旧类型
 * {@code DECISION_TABLE}/{@code SCORECARD}（同一实现，按构造入参 {@code supportedType} 区分）。
 * 具体执行哪种工具：
 * <ol>
 *   <li>节点类型为旧 DECISION_TABLE/SCORECARD → 直接对应；</li>
 *   <li>节点类型为 DECISION_TOOL → 按 {@code node.refType()}
 *       （DECISION_TABLE/SCORECARD/DECISION_TREE/DECISION_MATRIX）解析；
 *       refType 缺省时按内联定义映射中能命中 refId 的一类推断。</li>
 * </ol>
 *
 * <p><b>赋值字段登记（R9.1/R9.2）</b>：各工具产出登记为赋值字段供后续节点引用——
 * 评分卡登记 {@code lastScore}/{@code lastLevel}；任一工具产出决策登记 {@code lastDecision}；
 * 命中决策并入决策流累计结果。
 *
 * <p><b>自动执行关联资产（11.2，R9.3）</b>：各 Evaluator 自身<strong>不</strong>级联执行被引用的下级资产
 * （只读上下文字段值，详见 {@link AssetCascadeResolver} 注释「现有级联现状」）。故本处理器在执行主资产前，
 * 先经 {@link AssetCascadeResolver} 按主资产定义上声明的关联资产（{@code referencedAssets}）<strong>递归</strong>
 * 执行被引用资产并把其产出注入上下文（如「节点引用矩阵 B、矩阵 B 引用评分卡 C」时先算 C 再算 B），
 * 级联产出经 {@link NodeResult#cascaded()} 上报供链路记录展示（R9.4）。
 *
 * <p><b>运行期降级（R6.4/R6.6）</b>：引用资产（内联定义）不存在时返回空结果，不产决策、不中断流程。
 */
public final class DecisionToolNodeHandler implements NodeHandler {

    private final DecisionFlowDef.NodeType supportedType;
    private final DecisionTableEvaluator decisionTableEvaluator;
    private final ScorecardEvaluator scorecardEvaluator;
    private final DecisionTreeEvaluator decisionTreeEvaluator;
    private final DecisionMatrixEvaluator decisionMatrixEvaluator;
    /** 关联资产自动级联执行器（11.2，R9.3）。 */
    private final AssetCascadeResolver cascadeResolver;

    public DecisionToolNodeHandler(DecisionFlowDef.NodeType supportedType,
                                   DecisionTableEvaluator decisionTableEvaluator,
                                   ScorecardEvaluator scorecardEvaluator,
                                   DecisionTreeEvaluator decisionTreeEvaluator,
                                   DecisionMatrixEvaluator decisionMatrixEvaluator) {
        this.supportedType = supportedType;
        this.decisionTableEvaluator = decisionTableEvaluator;
        this.scorecardEvaluator = scorecardEvaluator;
        this.decisionTreeEvaluator = decisionTreeEvaluator;
        this.decisionMatrixEvaluator = decisionMatrixEvaluator;
        this.cascadeResolver = new AssetCascadeResolver(
                decisionTableEvaluator, scorecardEvaluator, decisionTreeEvaluator, decisionMatrixEvaluator);
    }

    @Override
    public DecisionFlowDef.NodeType supportedType() {
        return supportedType;
    }

    @Override
    public NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx) {
        Tool tool = resolveTool(node, ctx.def());
        return switch (tool) {
            case DECISION_TABLE -> handleTable(node, ctx);
            case SCORECARD -> handleScorecard(node, ctx);
            case DECISION_TREE -> handleTree(node, ctx);
            case DECISION_MATRIX -> handleMatrix(node, ctx);
            case NONE -> NodeResult.empty();
        };
    }

    private NodeResult handleTable(DecisionFlowDef.Node node, FlowContext ctx) {
        Map<Long, DecisionTableDef> tables = ctx.def().decisionTables();
        DecisionTableDef table = tables == null ? null : tables.get(node.refId());
        if (table == null) {
            // 引用资产不存在：运行期降级（不产决策、不中断），由引擎/上层记录
            return NodeResult.empty();
        }
        // R9.3：执行主资产前自动级联执行其声明的下级关联资产，产出注入上下文
        Map<String, Object> cascaded = cascade(table.referencedAssets(),
                AssetRef.AssetType.DECISION_TABLE, node.refId(), ctx);
        List<HitDecision> hits = decisionTableEvaluator.evaluate(table, ctx.env());
        Map<String, Object> assignments = new HashMap<>();
        if (!hits.isEmpty()) {
            assignments.put("lastDecision", strongest(hits).name());
        }
        return new NodeResult(hits, assignments, cascaded);
    }

    private NodeResult handleScorecard(DecisionFlowDef.Node node, FlowContext ctx) {
        Map<Long, ScorecardDef> cards = ctx.def().scorecards();
        ScorecardDef card = cards == null ? null : cards.get(node.refId());
        if (card == null) {
            return NodeResult.empty();
        }
        // R9.3：先级联执行被引用资产
        Map<String, Object> cascaded = cascade(card.referencedAssets(),
                AssetRef.AssetType.SCORECARD, node.refId(), ctx);
        ScorecardResult r = scorecardEvaluator.evaluate(card, ctx.env());
        Map<String, Object> assignments = new HashMap<>();
        assignments.put("lastScore", r.totalScore());
        if (r.level() != null) {
            assignments.put("lastLevel", r.level());
        }
        List<HitDecision> hits;
        if (r.hitDecision() != null) {
            hits = List.of(r.hitDecision());
            assignments.put("lastDecision", r.hitDecision().decision().name());
        } else {
            hits = List.of();
        }
        return new NodeResult(hits, assignments, cascaded);
    }

    /** 决策树工具（S8，9.3 接入）：从根节点选满足分支至叶子，叶子产出命中决策。 */
    private NodeResult handleTree(DecisionFlowDef.Node node, FlowContext ctx) {
        Map<Long, DecisionTreeDef> trees = ctx.def().decisionTrees();
        DecisionTreeDef tree = trees == null ? null : trees.get(node.refId());
        if (tree == null) {
            return NodeResult.empty();
        }
        // R9.3：先级联执行被引用资产
        Map<String, Object> cascaded = cascade(tree.referencedAssets(),
                AssetRef.AssetType.DECISION_TREE, node.refId(), ctx);
        DecisionTreeEvaluator.Result r = decisionTreeEvaluator.evaluate(tree, ctx.env());
        if (r.hit() == null) {
            return new NodeResult(List.of(), Map.of(), cascaded);
        }
        Map<String, Object> assignments = new HashMap<>();
        assignments.put("lastDecision", r.hit().decision().name());
        return new NodeResult(List.of(r.hit()), assignments, cascaded);
    }

    /** 决策矩阵工具（S9，9.3 接入）：行/列维度定位单元格，命中单元格产出决策。 */
    private NodeResult handleMatrix(DecisionFlowDef.Node node, FlowContext ctx) {
        Map<Long, DecisionMatrixDef> matrices = ctx.def().decisionMatrices();
        DecisionMatrixDef matrix = matrices == null ? null : matrices.get(node.refId());
        if (matrix == null) {
            return NodeResult.empty();
        }
        // R9.3：先级联执行被引用资产（如矩阵行/列维度变量取自一张评分卡得分）
        Map<String, Object> cascaded = cascade(matrix.referencedAssets(),
                AssetRef.AssetType.DECISION_MATRIX, node.refId(), ctx);
        DecisionMatrixEvaluator.Result r = decisionMatrixEvaluator.evaluate(matrix, ctx.env());
        if (r.hit() == null) {
            return new NodeResult(List.of(), Map.of(), cascaded);
        }
        Map<String, Object> assignments = new HashMap<>();
        assignments.put("lastDecision", r.hit().decision().name());
        return new NodeResult(List.of(r.hit()), assignments, cascaded);
    }

    /**
     * 执行主资产前的关联资产自动级联（11.2，R9.3）。
     *
     * <p>以「主资产自身」预置进已访问集合（防止下级资产再引用回主资产形成环），委派
     * {@link AssetCascadeResolver} 递归执行 {@code refs} 声明的被引用资产并把产出注入
     * {@code ctx.env()}，返回级联产出明细供链路记录（R9.4）。
     */
    private Map<String, Object> cascade(List<AssetRef> refs, AssetRef.AssetType selfType,
                                        Long selfId, FlowContext ctx) {
        if (refs == null || refs.isEmpty()) {
            return Map.of();
        }
        Set<String> visited = new HashSet<>();
        if (selfId != null) {
            visited.add(selfType.name() + ":" + selfId); // 预置主资产自身，防止级联回环
        }
        return cascadeResolver.cascade(refs, ctx.def(), ctx.env(), visited);
    }

    /** 取命中决策中严格性最高者（REJECT>REVIEW>PASS）。 */
    private Decision strongest(List<HitDecision> hits) {
        return hits.stream()
                .map(HitDecision::decision)
                .max((a, b) -> Integer.compare(a.strictness(), b.strictness()))
                .orElse(Decision.PASS);
    }

    /** 解析本节点应执行的决策工具类型。 */
    private Tool resolveTool(DecisionFlowDef.Node node, DecisionFlowDef def) {
        // 旧节点类型直接对应
        if (node.type() == DecisionFlowDef.NodeType.DECISION_TABLE) {
            return Tool.DECISION_TABLE;
        }
        if (node.type() == DecisionFlowDef.NodeType.SCORECARD) {
            return Tool.SCORECARD;
        }
        // 统一 DECISION_TOOL：优先按 refType 解析
        String refType = node.refType();
        if (refType != null) {
            String t = refType.trim().toUpperCase();
            // 注意：先判定更具体的 MATRIX/TREE，避免 "TABLE" 子串误命中
            if (t.contains("MATRIX")) {
                return Tool.DECISION_MATRIX;
            }
            if (t.contains("TREE")) {
                return Tool.DECISION_TREE;
            }
            if (t.contains("TABLE")) {
                return Tool.DECISION_TABLE;
            }
            if (t.contains("SCORECARD") || t.contains("SCORE_CARD")) {
                return Tool.SCORECARD;
            }
        }
        // refType 缺省：按内联定义映射能命中 refId 的一类推断（兼容旧数据/简化请求）
        Long refId = node.refId();
        if (containsKey(def.decisionTables(), refId)) {
            return Tool.DECISION_TABLE;
        }
        if (containsKey(def.scorecards(), refId)) {
            return Tool.SCORECARD;
        }
        if (containsKey(def.decisionTrees(), refId)) {
            return Tool.DECISION_TREE;
        }
        if (containsKey(def.decisionMatrices(), refId)) {
            return Tool.DECISION_MATRIX;
        }
        return Tool.NONE;
    }

    private boolean containsKey(Map<Long, ?> map, Long key) {
        return map != null && key != null && map.containsKey(key);
    }

    private enum Tool { DECISION_TABLE, SCORECARD, DECISION_TREE, DECISION_MATRIX, NONE }
}
