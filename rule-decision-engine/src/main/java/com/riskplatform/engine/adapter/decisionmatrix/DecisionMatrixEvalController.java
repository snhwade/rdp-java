package com.riskplatform.engine.adapter.decisionmatrix;

import com.riskplatform.engine.domain.decisionmatrix.DecisionMatrixDef;
import com.riskplatform.engine.domain.decisionmatrix.DecisionMatrixEvaluator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 决策矩阵执行 REST 适配器（S9）。
 *
 * <p>{@code POST /api/v1/decision-matrices/evaluate}：传入矩阵定义与上下文，
 * 返回最终决策与命中的行/列索引。
 */
@RestController
@RequestMapping("/api/v1/decision-matrices")
public class DecisionMatrixEvalController {

    private final DecisionMatrixEvaluator evaluator;

    public DecisionMatrixEvalController(DecisionMatrixEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @PostMapping("/evaluate")
    public EvalView evaluate(@RequestBody EvalRequest req) {
        DecisionMatrixDef def = new DecisionMatrixDef(req.id(), req.rowVar(), req.rowBins(),
                req.colVar(), req.colBins(), req.cells());
        DecisionMatrixEvaluator.Result r = evaluator.evaluate(def, req.context() == null ? Map.of() : req.context());
        String decision = r.hit() == null ? "PASS" : r.hit().decision().name();
        return new EvalView(decision, r.rowIndex(), r.colIndex());
    }

    public record EvalRequest(
            Long id,
            String rowVar,
            List<DecisionMatrixDef.Bin> rowBins,
            String colVar,
            List<DecisionMatrixDef.Bin> colBins,
            List<DecisionMatrixDef.Cell> cells,
            Map<String, Object> context) {
    }

    public record EvalView(String finalDecision, int rowIndex, int colIndex) {
    }
}
