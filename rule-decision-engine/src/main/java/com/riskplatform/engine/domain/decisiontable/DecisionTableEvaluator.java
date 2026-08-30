package com.riskplatform.engine.domain.decisiontable;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 决策表执行器（S2）。
 *
 * <p>对一张决策表与一组输入上下文求值，产出命中决策列表（{@link HitDecision}），
 * 与规则命中同构，可直接并入既有决策聚合（{@code DecisionAggregator}）。
 *
 * <p>命中策略：
 * <ul>
 *   <li>FIRST：自上而下首个全部列条件满足的行命中，返回该行单个命中决策；</li>
 *   <li>COLLECT：收集所有满足的行，返回全部命中决策。</li>
 * </ul>
 *
 * <p>命中决策的 ruleId 用「决策表 id 取负」标识来源，避免与真实规则 id 冲突。
 */
public class DecisionTableEvaluator {

    /**
     * 对决策表求值。
     *
     * @param def     决策表定义
     * @param context 输入上下文（变量名 → 值）
     * @return 命中决策列表（无命中返回空列表）
     */
    public List<HitDecision> evaluate(DecisionTableDef def, Map<String, Object> context) {
        List<HitDecision> hits = new ArrayList<>();
        long sourceId = def.id() == null ? 0L : -def.id();
        for (DecisionTableDef.Row row : def.rows()) {
            if (rowMatches(row, context)) {
                hits.add(new HitDecision(sourceId, row.priority(), Decision.valueOf(row.decision())));
                if (def.hitPolicy() == DecisionTableDef.HitPolicy.FIRST) {
                    return hits; // 首行命中即返回
                }
            }
        }
        return hits;
    }

    private boolean rowMatches(DecisionTableDef.Row row, Map<String, Object> context) {
        for (DecisionTableDef.Condition c : row.conditions()) {
            if (!conditionMatches(c, context)) {
                return false; // 任一列条件不满足则该行不命中
            }
        }
        return true;
    }

    private boolean conditionMatches(DecisionTableDef.Condition c, Map<String, Object> context) {
        Object raw = context.get(c.var());
        switch (c.op()) {
            case IN:
                if (raw == null || c.values() == null) {
                    return false;
                }
                return c.values().contains(String.valueOf(raw));
            case BETWEEN: {
                Double v = toDouble(raw);
                return v != null && c.value() != null && c.value2() != null
                        && v >= c.value() && v <= c.value2();
            }
            case EQ:
            case NE: {
                // 数值优先按数值比较，否则按字符串比较
                Double v = toDouble(raw);
                if (v != null && c.value() != null) {
                    boolean eq = v.doubleValue() == c.value().doubleValue();
                    return c.op() == DecisionTableDef.Op.EQ ? eq : !eq;
                }
                boolean eq = raw != null && c.values() != null && !c.values().isEmpty()
                        && String.valueOf(raw).equals(c.values().get(0));
                return c.op() == DecisionTableDef.Op.EQ ? eq : !eq;
            }
            default: {
                Double v = toDouble(raw);
                if (v == null || c.value() == null) {
                    return false;
                }
                double t = c.value();
                return switch (c.op()) {
                    case GT -> v > t;
                    case GE -> v >= t;
                    case LT -> v < t;
                    case LE -> v <= t;
                    default -> false;
                };
            }
        }
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
}
