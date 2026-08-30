package com.riskplatform.engine.adapter.decisiontree;

import com.riskplatform.engine.domain.decisiontree.DecisionTreeDef;
import com.riskplatform.engine.domain.decisiontree.DecisionTreeEvaluator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 决策树执行 REST 适配器（S8）。
 *
 * <p>{@code POST /api/v1/decision-trees/evaluate}：传入决策树定义与上下文，
 * 返回最终决策与下钻路径。
 */
@RestController
@RequestMapping("/api/v1/decision-trees")
public class DecisionTreeEvalController {

    private final DecisionTreeEvaluator evaluator;

    public DecisionTreeEvalController(DecisionTreeEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @PostMapping("/evaluate")
    public EvalView evaluate(@RequestBody EvalRequest req) {
        DecisionTreeDef def = new DecisionTreeDef(req.id(), req.rootNodeId(), req.nodes());
        DecisionTreeEvaluator.Result r = evaluator.evaluate(def, req.context() == null ? Map.of() : req.context());
        String decision = r.hit() == null ? "PASS" : r.hit().decision().name();
        return new EvalView(decision, r.path());
    }

    public record EvalRequest(
            Long id,
            String rootNodeId,
            List<DecisionTreeDef.Node> nodes,
            Map<String, Object> context) {
    }

    public record EvalView(String finalDecision, List<String> path) {
    }
}
