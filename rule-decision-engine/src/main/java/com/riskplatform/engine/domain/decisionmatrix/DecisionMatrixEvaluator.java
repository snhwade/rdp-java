package com.riskplatform.engine.domain.decisionmatrix;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.List;
import java.util.Map;

/**
 * 决策矩阵执行器（S9）。
 *
 * <p>取行维度/列维度变量值，分别定位命中区间索引，按 (row,col) 查单元格 → 产出命中决策。
 * 行或列无命中区间，或无对应单元格 → 不产决策（返回 null）。
 */
public class DecisionMatrixEvaluator {

    /** 返回 { hit?, rowIndex, colIndex }。无命中 hit 为 null、索引为 -1。 */
    public Result evaluate(DecisionMatrixDef def, Map<String, Object> context) {
        int rowIdx = binIndex(def.rowBins(), toDouble(context.get(def.rowVar())));
        int colIdx = binIndex(def.colBins(), toDouble(context.get(def.colVar())));
        if (rowIdx < 0 || colIdx < 0) {
            return new Result(null, rowIdx, colIdx);
        }
        long sourceId = def.id() == null ? 0L : -def.id();
        for (DecisionMatrixDef.Cell c : def.cells()) {
            if (c.row() == rowIdx && c.col() == colIdx) {
                HitDecision hit = new HitDecision(sourceId, c.priority(), Decision.valueOf(c.decision()));
                return new Result(hit, rowIdx, colIdx);
            }
        }
        return new Result(null, rowIdx, colIdx);
    }

    /** 找值命中的区间索引 [min,max)，无命中返回 -1。 */
    private int binIndex(List<DecisionMatrixDef.Bin> bins, Double value) {
        if (value == null || bins == null) {
            return -1;
        }
        for (int i = 0; i < bins.size(); i++) {
            DecisionMatrixDef.Bin b = bins.get(i);
            if (value >= b.min() && value < b.max()) {
                return i;
            }
        }
        return -1;
    }

    private Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 执行结果。 */
    public record Result(HitDecision hit, int rowIndex, int colIndex) {
    }
}
