package com.riskplatform.engine.domain.decisiontree;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策树执行器（S8）。
 *
 * <p>从根节点出发：内部节点按 children 分支条件（Aviator）依次判断，进入首个满足条件的子节点；
 * 到叶子节点产出命中决策（ruleId=-树id），与规则命中同构并入决策聚合。
 * 内部节点无任何分支满足 → 不产决策（视为未命中）。含步数上限防环。
 */
public class DecisionTreeEvaluator {

    private static final int MAX_DEPTH = 100;

    private final AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();

    /** 返回 { hit?, path }。无命中时 hit 为 null。 */
    public Result evaluate(DecisionTreeDef def, Map<String, Object> context) {
        Map<String, DecisionTreeDef.Node> byId = new HashMap<>();
        for (DecisionTreeDef.Node n : def.nodes()) {
            byId.put(n.nodeId(), n);
        }
        List<String> path = new ArrayList<>();
        String currentId = def.rootNodeId();
        int depth = 0;
        long sourceId = def.id() == null ? 0L : -def.id();

        while (currentId != null && depth++ < MAX_DEPTH) {
            DecisionTreeDef.Node node = byId.get(currentId);
            if (node == null) {
                break;
            }
            path.add(currentId);

            if (node.leaf()) {
                if (node.decision() != null) {
                    int priority = node.priority() == null ? 100 : node.priority();
                    HitDecision hit = new HitDecision(sourceId, priority, Decision.valueOf(node.decision()));
                    return new Result(hit, path);
                }
                return new Result(null, path); // 叶子但无决策
            }

            // 内部节点：选首个满足条件的子分支
            String next = null;
            if (node.children() != null) {
                for (DecisionTreeDef.Branch b : node.children()) {
                    if (evalCondition(b.condition(), context)) {
                        next = b.childNodeId();
                        break;
                    }
                }
            }
            currentId = next; // 无满足分支 → null → 结束，无命中
        }
        return new Result(null, path);
    }

    private boolean evalCondition(String condition, Map<String, Object> context) {
        if (condition == null || condition.isBlank()) {
            return true; // 空条件视为兜底分支
        }
        try {
            Object r = aviator.execute(condition, context, true);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    /** 执行结果。 */
    public record Result(HitDecision hit, List<String> path) {
    }
}
