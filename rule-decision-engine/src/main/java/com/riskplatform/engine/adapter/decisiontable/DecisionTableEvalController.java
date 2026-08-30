package com.riskplatform.engine.adapter.decisiontable;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decision.DecisionAggregator;
import com.riskplatform.engine.domain.decisiontable.DecisionTableDef;
import com.riskplatform.engine.domain.decisiontable.DecisionTableEvaluator;
import com.riskplatform.engine.domain.rule.HitDecision;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 决策表执行 REST 适配器（S2）。
 *
 * <p>{@code POST /api/v1/decision-tables/evaluate}：传入决策表定义与上下文，返回命中决策与
 * 聚合后的最终决策。供网关编排或测试驱动；引擎动态加载决策表后亦可由内部直接调用执行器。
 */
@RestController
@RequestMapping("/api/v1/decision-tables")
public class DecisionTableEvalController {

    private final DecisionTableEvaluator evaluator;
    private final DecisionAggregator aggregator;

    public DecisionTableEvalController(DecisionTableEvaluator evaluator, DecisionAggregator aggregator) {
        this.evaluator = evaluator;
        this.aggregator = aggregator;
    }

    @PostMapping("/evaluate")
    public EvalView evaluate(@RequestBody EvalRequest req) {
        DecisionTableDef def = new DecisionTableDef(
                req.id(), req.name(),
                DecisionTableDef.HitPolicy.valueOf(req.hitPolicy()),
                req.rows());
        List<HitDecision> hits = evaluator.evaluate(def, req.context() == null ? Map.of() : req.context());
        Decision finalDecision = aggregator.aggregate(hits);
        return new EvalView(
                finalDecision.name(),
                hits.stream().map(h -> new HitView(h.ruleId(), h.priority(), h.decision().name())).toList());
    }

    public record EvalRequest(
            Long id,
            String name,
            String hitPolicy,
            List<DecisionTableDef.Row> rows,
            Map<String, Object> context) {
    }

    public record HitView(long sourceId, int priority, String decision) {
    }

    public record EvalView(String finalDecision, List<HitView> hits) {
    }
}
