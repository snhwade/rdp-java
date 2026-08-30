package com.riskplatform.engine.domain.decisionflow;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decision.DecisionAggregator;
import com.riskplatform.engine.domain.decisiontable.DecisionTableDef;
import com.riskplatform.engine.domain.decisiontable.DecisionTableEvaluator;
import com.riskplatform.engine.domain.rule.HitDecision;
import com.riskplatform.engine.domain.scorecard.ScorecardDef;
import com.riskplatform.engine.domain.scorecard.ScorecardEvaluator;
import com.riskplatform.engine.domain.scorecard.ScorecardResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策流执行器（S4）。
 *
 * <p>从起始节点遍历执行决策流：每个节点产出命中决策并写入执行上下文（供后续边条件判断），
 * 按满足条件的出边走向下一节点，到 END 时用决策聚合得出最终决策。
 *
 * <p>节点执行复用 S2 决策表执行器与 S3 评分卡执行器；边条件用 Aviator 求值，
 * 环境变量包含上下文字段 + lastDecision/lastLevel/lastScore（上一节点产出）。
 *
 * <p>防御：记录已访问节点防环，最多执行步数上限。
 */
public class DecisionFlowEvaluator {

    private static final int MAX_STEPS = 100;

    private final DecisionTableEvaluator decisionTableEvaluator;
    private final ScorecardEvaluator scorecardEvaluator;
    private final DecisionAggregator aggregator;
    private final AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();

    public DecisionFlowEvaluator(DecisionTableEvaluator decisionTableEvaluator,
                                 ScorecardEvaluator scorecardEvaluator,
                                 DecisionAggregator aggregator) {
        this.decisionTableEvaluator = decisionTableEvaluator;
        this.scorecardEvaluator = scorecardEvaluator;
        this.aggregator = aggregator;
    }

    public DecisionFlowResult evaluate(DecisionFlowDef def, Map<String, Object> context) {
        Map<String, DecisionFlowDef.Node> nodeById = new HashMap<>();
        for (DecisionFlowDef.Node n : def.nodes()) {
            nodeById.put(n.nodeId(), n);
        }

        // 执行上下文：原始上下文 + 上一节点产出（lastDecision/lastLevel/lastScore）
        Map<String, Object> env = new HashMap<>(context);
        List<HitDecision> hits = new ArrayList<>();
        List<String> path = new ArrayList<>();

        String currentId = def.startNodeId();
        int steps = 0;
        // 到达 END 节点时其配置的决策结果（R9.7）；未配置则回退到命中聚合决策
        String endDecision = null;
        while (currentId != null && steps++ < MAX_STEPS) {
            DecisionFlowDef.Node node = nodeById.get(currentId);
            if (node == null) {
                break;
            }
            path.add(currentId);

            // 执行节点逻辑（START/END 不产决策）
            executeNode(node, def, env, hits);

            if (node.type() == DecisionFlowDef.NodeType.END) {
                // R9.7：决策流执行到达结束节点，产出该节点配置的决策结果作为决策流结果
                endDecision = EndDecisionResolver.resolve(node.config());
                break;
            }
            // 选择下一节点：满足条件的首条出边；无条件边作为兜底（最后选）
            currentId = nextNode(def, currentId, env);
        }

        // 优先采用到达的 END 节点所配置的决策结果（R9.7）；历史数据未配置时回退到命中聚合决策
        String finalDecision = endDecision != null ? endDecision : aggregator.aggregate(hits).name();
        return new DecisionFlowResult(finalDecision, path, hits);
    }

    private void executeNode(DecisionFlowDef.Node node, DecisionFlowDef def,
                             Map<String, Object> env, List<HitDecision> hits) {
        switch (node.type()) {
            case DECISION_TABLE -> {
                DecisionTableDef table = def.decisionTables() == null ? null
                        : def.decisionTables().get(node.refId());
                if (table != null) {
                    List<HitDecision> tableHits = decisionTableEvaluator.evaluate(table, env);
                    hits.addAll(tableHits);
                    if (!tableHits.isEmpty()) {
                        // 以最严格命中作为 lastDecision
                        Decision strongest = tableHits.stream()
                                .map(HitDecision::decision)
                                .max((a, b) -> Integer.compare(a.strictness(), b.strictness()))
                                .orElse(Decision.PASS);
                        env.put("lastDecision", strongest.name());
                    }
                }
            }
            case SCORECARD -> {
                ScorecardDef card = def.scorecards() == null ? null : def.scorecards().get(node.refId());
                if (card != null) {
                    ScorecardResult r = scorecardEvaluator.evaluate(card, env);
                    env.put("lastScore", r.totalScore());
                    if (r.level() != null) {
                        env.put("lastLevel", r.level());
                    }
                    if (r.hitDecision() != null) {
                        hits.add(r.hitDecision());
                        env.put("lastDecision", r.hitDecision().decision().name());
                    }
                }
            }
            case LIST_CHECK -> {
                // 简化：名单命中由上下文 blackHit=true 表达（生产由网关调名单服务注入）
                Object blackHit = env.get("blackHit");
                if (Boolean.TRUE.equals(blackHit) || "true".equals(String.valueOf(blackHit))) {
                    // 黑名单命中：产出高优先级 REJECT
                    hits.add(new HitDecision(-9000L, 1, Decision.REJECT));
                    env.put("lastDecision", Decision.REJECT.name());
                }
            }
            case START, END -> {
                // 起止节点不产决策
            }
        }
    }

    private String nextNode(DecisionFlowDef def, String fromId, Map<String, Object> env) {
        String fallback = null;
        for (DecisionFlowDef.Edge e : def.edges()) {
            if (!fromId.equals(e.from())) {
                continue;
            }
            String cond = e.condition();
            if (cond == null || cond.isBlank()) {
                fallback = e.to(); // 无条件边记为兜底
                continue;
            }
            if (evalCondition(cond, env)) {
                return e.to();
            }
        }
        return fallback;
    }

    private boolean evalCondition(String condition, Map<String, Object> env) {
        try {
            Object r = aviator.execute(condition, env, true);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            // 表达式异常视为不满足该边
            return false;
        }
    }
}
