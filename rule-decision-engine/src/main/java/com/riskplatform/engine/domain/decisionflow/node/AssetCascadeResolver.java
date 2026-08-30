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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关联资产自动级联执行器（扩展阶段 11.2，R9.3）。
 *
 * <h3>现有级联现状（grep 核实结论）</h3>
 * <p>执行侧四类决策工具执行器
 * （{@code DecisionTableEvaluator}/{@code ScorecardEvaluator}/{@code DecisionTreeEvaluator}/
 * {@code DecisionMatrixEvaluator}）均<strong>不</strong>内部级联加载执行被引用的下级资产——它们只从
 * 传入的执行上下文 {@code Map<String,Object>} 读取已就绪的字段/指标值，对「某输入变量其实应由另一个
 * 决策资产先算出」一无所知。也即：手册中「决策矩阵引用评分卡」这种资产间引用，原实现要求调用方
 * 预先把评分卡得分放进上下文，否则矩阵取不到该变量。因此 R9.3「自动级联执行被引用资产」在原实现里
 * 是<strong>缺口</strong>。
 *
 * <h3>本任务补的部分</h3>
 * <p>在<strong>决策流节点处理器层</strong>（{@code DecisionToolNodeHandler}）补齐级联：节点执行主资产
 * （如决策矩阵 B）之前，先按主资产定义上声明的 {@link AssetRef} 列表，<strong>递归</strong>执行其引用的
 * 下级资产（如评分卡 C），把每个被引用资产的产出以其 {@link AssetRef#resolveOutputKey()} 键登记进执行
 * 上下文，再执行主资产——从而实现「节点 A 引用矩阵 B、矩阵 B 引用评分卡 C」时 B、C 自动级联执行，
 * 无需在决策流中为 C 显式配置节点（R9.3）。被引用资产的定义同样取自决策流内联定义映射
 * （{@code def.scorecards()/decisionTables()/decisionTrees()/decisionMatrices()}）。
 *
 * <p>产出登记取值约定：
 * <ul>
 *   <li>评分卡 → 登记其总分（{@code totalScore}），供上级资产作为数值型维度变量；</li>
 *   <li>决策表/决策树/决策矩阵 → 登记其命中决策名（最严格者，无命中为 null），供上级资产作为字符串维度变量。</li>
 * </ul>
 *
 * <p>防护：以「已访问资产 (type,id) 集合」防御资产间循环引用（A→B→A），命中即跳过（不再展开），
 * 并以最大级联深度上限兜底。引用资产在内联映射中不存在时跳过该引用（降级，不中断）。
 */
public final class AssetCascadeResolver {

    private static final Logger log = LoggerFactory.getLogger(AssetCascadeResolver.class);

    /** 级联递归深度上限（防御异常的超深引用链）。 */
    private static final int MAX_DEPTH = 16;

    private final DecisionTableEvaluator decisionTableEvaluator;
    private final ScorecardEvaluator scorecardEvaluator;
    private final DecisionTreeEvaluator decisionTreeEvaluator;
    private final DecisionMatrixEvaluator decisionMatrixEvaluator;

    public AssetCascadeResolver(DecisionTableEvaluator decisionTableEvaluator,
                                ScorecardEvaluator scorecardEvaluator,
                                DecisionTreeEvaluator decisionTreeEvaluator,
                                DecisionMatrixEvaluator decisionMatrixEvaluator) {
        this.decisionTableEvaluator = decisionTableEvaluator;
        this.scorecardEvaluator = scorecardEvaluator;
        this.decisionTreeEvaluator = decisionTreeEvaluator;
        this.decisionMatrixEvaluator = decisionMatrixEvaluator;
    }

    /**
     * 在执行主资产前，按其声明的关联资产列表自动级联执行被引用资产，把产出注入 {@code env}。
     *
     * @param refs    主资产声明的下级关联资产引用（可空/空时不做任何级联）
     * @param def     决策流定义（提供被引用资产的内联定义映射）
     * @param env     执行上下文（被引用资产的产出会写入这里供主资产取用）
     * @param visited 本次节点执行已访问的资产集合（防环；调用方传入新建集合并已含主资产自身）
     * @return 本次级联产出明细（登记键→产出值），供链路记录展示（R9.4）；无级联时为空 map
     */
    public Map<String, Object> cascade(List<AssetRef> refs,
                                       DecisionFlowDef def,
                                       Map<String, Object> env,
                                       Set<String> visited) {
        Map<String, Object> produced = new LinkedHashMap<>();
        cascadeInternal(refs, def, env, visited, produced, 0);
        return produced;
    }

    private void cascadeInternal(List<AssetRef> refs,
                                 DecisionFlowDef def,
                                 Map<String, Object> env,
                                 Set<String> visited,
                                 Map<String, Object> produced,
                                 int depth) {
        if (refs == null || refs.isEmpty() || depth >= MAX_DEPTH) {
            if (depth >= MAX_DEPTH) {
                log.warn("关联资产级联深度越界，停止展开: depth={}", depth);
            }
            return;
        }
        for (AssetRef ref : refs) {
            if (ref == null || ref.type() == null || ref.refId() == null) {
                continue;
            }
            String key = ref.type().name() + ":" + ref.refId();
            if (!visited.add(key)) {
                // 已访问（资产间循环引用 A->B->A 或重复引用）：跳过，不再展开
                log.warn("关联资产循环/重复引用，跳过: {}", key);
                continue;
            }
            // 先递归执行「被引用资产的被引用资产」（级联链 B->C->...），再执行被引用资产本身
            Object output = executeReferenced(ref, def, env, visited, produced, depth);
            if (output != null) {
                String outKey = ref.resolveOutputKey();
                env.put(outKey, output);
                produced.put(outKey, output);
            }
        }
    }

    /** 执行单个被引用资产（先级联其下级），返回其产出值（评分卡总分 / 工具命中决策名）。 */
    private Object executeReferenced(AssetRef ref,
                                     DecisionFlowDef def,
                                     Map<String, Object> env,
                                     Set<String> visited,
                                     Map<String, Object> produced,
                                     int depth) {
        Long refId = ref.refId();
        switch (ref.type()) {
            case SCORECARD -> {
                ScorecardDef card = get(def.scorecards(), refId);
                if (card == null) {
                    log.warn("关联评分卡不存在，跳过级联: refId={}", refId);
                    return null;
                }
                cascadeInternal(card.referencedAssets(), def, env, visited, produced, depth + 1);
                ScorecardResult r = scorecardEvaluator.evaluate(card, env);
                return r.totalScore();
            }
            case DECISION_TABLE -> {
                DecisionTableDef table = get(def.decisionTables(), refId);
                if (table == null) {
                    log.warn("关联决策表不存在，跳过级联: refId={}", refId);
                    return null;
                }
                cascadeInternal(table.referencedAssets(), def, env, visited, produced, depth + 1);
                List<HitDecision> hits = decisionTableEvaluator.evaluate(table, env);
                return strongestName(hits);
            }
            case DECISION_TREE -> {
                DecisionTreeDef tree = get(def.decisionTrees(), refId);
                if (tree == null) {
                    log.warn("关联决策树不存在，跳过级联: refId={}", refId);
                    return null;
                }
                cascadeInternal(tree.referencedAssets(), def, env, visited, produced, depth + 1);
                DecisionTreeEvaluator.Result r = decisionTreeEvaluator.evaluate(tree, env);
                return r.hit() == null ? null : r.hit().decision().name();
            }
            case DECISION_MATRIX -> {
                DecisionMatrixDef matrix = get(def.decisionMatrices(), refId);
                if (matrix == null) {
                    log.warn("关联决策矩阵不存在，跳过级联: refId={}", refId);
                    return null;
                }
                cascadeInternal(matrix.referencedAssets(), def, env, visited, produced, depth + 1);
                DecisionMatrixEvaluator.Result r = decisionMatrixEvaluator.evaluate(matrix, env);
                return r.hit() == null ? null : r.hit().decision().name();
            }
            default -> {
                return null;
            }
        }
    }

    private String strongestName(List<HitDecision> hits) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        return hits.stream()
                .map(HitDecision::decision)
                .max((a, b) -> Integer.compare(a.strictness(), b.strictness()))
                .orElse(Decision.PASS)
                .name();
    }

    private <T> T get(Map<Long, T> map, Long key) {
        return (map == null || key == null) ? null : map.get(key);
    }
}
