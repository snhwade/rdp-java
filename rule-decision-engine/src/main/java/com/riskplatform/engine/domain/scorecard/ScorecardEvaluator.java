package com.riskplatform.engine.domain.scorecard;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.List;
import java.util.Map;

/**
 * 评分卡执行器（S3）。
 *
 * <p>对一张评分卡与一组输入上下文求值：
 * <ol>
 *   <li>每个变量：找首个命中的条件区间(bin) → 取其分值；无命中用 defaultScore。</li>
 *   <li>总分 = Σ(命中区间分值 × 变量权重)。</li>
 *   <li>总分落入某等级区间 [minScore, maxScore) → 产出该等级与决策。</li>
 * </ol>
 *
 * <p>命中决策的 ruleId 用「评分卡 id 取负」标识来源，与规则/决策表命中同构，可并入决策聚合。
 */
public class ScorecardEvaluator {

    public ScorecardResult evaluate(ScorecardDef def, Map<String, Object> context) {
        double total = 0d;
        for (ScorecardDef.Variable v : def.variables()) {
            double score = scoreOfVariable(v, context);
            total += score * v.weight();
        }
        long sourceId = def.id() == null ? 0L : -def.id();
        for (ScorecardDef.Level level : def.levels()) {
            if (total >= level.minScore() && total < level.maxScore()) {
                HitDecision hit = new HitDecision(sourceId, level.priority(), Decision.valueOf(level.decision()));
                return new ScorecardResult(total, level.level(), hit);
            }
        }
        // 总分未落入任何等级区间：返回总分但不产出命中决策
        return new ScorecardResult(total, null, null);
    }

    /** 变量得分：首个命中区间的分值；无命中用缺省分。 */
    private double scoreOfVariable(ScorecardDef.Variable v, Map<String, Object> context) {
        Object raw = context.get(v.var());
        for (ScorecardDef.Bin bin : v.bins()) {
            if (binMatches(bin, raw)) {
                return bin.score();
            }
        }
        return v.defaultScore();
    }

    private boolean binMatches(ScorecardDef.Bin bin, Object raw) {
        switch (bin.op()) {
            case IN:
                return raw != null && bin.values() != null && bin.values().contains(String.valueOf(raw));
            case BETWEEN: {
                Double val = toDouble(raw);
                return val != null && bin.value() != null && bin.value2() != null
                        && val >= bin.value() && val <= bin.value2();
            }
            case EQ:
            case NE: {
                Double val = toDouble(raw);
                if (val != null && bin.value() != null) {
                    boolean eq = val.doubleValue() == bin.value().doubleValue();
                    return bin.op() == ScorecardDef.Op.EQ ? eq : !eq;
                }
                boolean eq = raw != null && bin.values() != null && !bin.values().isEmpty()
                        && String.valueOf(raw).equals(bin.values().get(0));
                return bin.op() == ScorecardDef.Op.EQ ? eq : !eq;
            }
            default: {
                Double val = toDouble(raw);
                if (val == null || bin.value() == null) {
                    return false;
                }
                double t = bin.value();
                return switch (bin.op()) {
                    case GT -> val > t;
                    case GE -> val >= t;
                    case LT -> val < t;
                    case LE -> val <= t;
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
