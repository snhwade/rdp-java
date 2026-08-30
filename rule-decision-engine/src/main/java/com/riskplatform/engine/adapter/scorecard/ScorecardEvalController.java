package com.riskplatform.engine.adapter.scorecard;

import com.riskplatform.engine.domain.scorecard.ScorecardDef;
import com.riskplatform.engine.domain.scorecard.ScorecardEvaluator;
import com.riskplatform.engine.domain.scorecard.ScorecardResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 评分卡执行 REST 适配器（S3）。
 *
 * <p>{@code POST /api/v1/scorecards/evaluate}：传入评分卡定义与上下文，返回总分、命中等级与决策。
 */
@RestController
@RequestMapping("/api/v1/scorecards")
public class ScorecardEvalController {

    private final ScorecardEvaluator evaluator;

    public ScorecardEvalController(ScorecardEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @PostMapping("/evaluate")
    public EvalView evaluate(@RequestBody EvalRequest req) {
        ScorecardDef def = new ScorecardDef(req.id(), req.name(), req.variables(), req.levels());
        ScorecardResult r = evaluator.evaluate(def, req.context() == null ? Map.of() : req.context());
        String decision = r.hitDecision() == null ? "PASS" : r.hitDecision().decision().name();
        Integer priority = r.hitDecision() == null ? null : r.hitDecision().priority();
        return new EvalView(r.totalScore(), r.level(), decision, priority);
    }

    public record EvalRequest(
            Long id,
            String name,
            List<ScorecardDef.Variable> variables,
            List<ScorecardDef.Level> levels,
            Map<String, Object> context) {
    }

    public record EvalView(double totalScore, String level, String decision, Integer priority) {
    }
}
