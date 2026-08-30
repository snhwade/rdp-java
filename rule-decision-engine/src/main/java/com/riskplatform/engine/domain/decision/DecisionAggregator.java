package com.riskplatform.engine.domain.decision;

import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.Comparator;
import java.util.List;

/**
 * 决策聚合算法（R6.1–R6.4）。
 *
 * <p>规则：
 * <ul>
 *   <li>无命中 → 默认放行 PASS（R6.4）；</li>
 *   <li>有命中 → 取决策优先级数值最大（优先级最高）的子集；</li>
 *   <li>在该子集中按严格性取最严格者（REJECT&gt;REVIEW&gt;PASS，R6.3）。</li>
 * </ul>
 *
 * <p>结果与输入顺序无关（置换不变）；增加优先级更低（数值更小）的决策不改变结果（单调）。
 */
public class DecisionAggregator {

    /**
     * 聚合命中决策，产出最终决策。
     *
     * @param hits 命中规则产生的决策（可空）
     * @return 最终决策
     */
    public Decision aggregate(List<HitDecision> hits) {
        if (hits == null || hits.isEmpty()) {
            return Decision.PASS; // R6.4
        }
        int maxPriority = hits.stream()
                .mapToInt(HitDecision::priority)
                .max()
                .orElseThrow();
        return hits.stream()
                .filter(h -> h.priority() == maxPriority)            // 最大优先级数值子集 R6.2
                .map(HitDecision::decision)
                .max(Comparator.comparingInt(Decision::strictness))  // 严格性最高 R6.3
                .orElse(Decision.PASS);
    }
}
