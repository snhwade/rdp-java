package com.riskplatform.engine.domain.score;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 评分规则触发分计算器（引擎运行面，R4.1/R4.2/R4.3/R4.6）。
 *
 * <p>纯函数 / 无状态组件：给定一条已触发的评分规则（{@link ScoreRule}，含基础分 + 动态分区间）
 * 与决策上下文（{@code Map<String,Object>}，含指标值），计算该规则的触发分：
 *
 * <pre>
 *   触发分 = baseScore + dynamicScore(指标值)        （R4.3）
 * </pre>
 *
 * <p>其中 {@code dynamicScore} 按指标值落入的动态分区间取分；区间采用左闭右开的确定性归入
 * （R4.6，具体开闭由 {@link ScoreDynamicBand} 的 inclusive 标志决定，配置侧已保证区间不重叠）。
 *
 * <p>缺失处理（在此显式约定）：
 * <ul>
 *   <li>指标在上下文中缺失、为空、或无法解析为数值时，动态分按 0 计。</li>
 *   <li>指标值不落入任何动态分区间时，动态分按 0 计。</li>
 *   <li>{@link ScoreRule#baseScore()} 为 null 时基础分按 0 计。</li>
 * </ul>
 *
 * <p>无状态、确定性：相同输入恒得相同结果，可直接用于属性测试（Property 3 确定性）。
 */
public class ScoreCalculator {

    /**
     * 计算单条触发评分规则的触发分。
     *
     * @param rule    已触发的评分规则
     * @param context 决策上下文（指标值等）
     * @return 触发分 = 基础分 + 动态分
     */
    public BigDecimal triggerScore(ScoreRule rule, Map<String, Object> context) {
        BigDecimal base = rule.baseScore() == null ? BigDecimal.ZERO : rule.baseScore();
        BigDecimal dynamic = dynamicScore(rule, context);
        return base.add(dynamic);
    }

    /**
     * 计算动态分：指标值落入的动态分区间得分；缺失/无匹配区间时为 0（R4.6）。
     *
     * @param rule    评分规则
     * @param context 决策上下文
     * @return 动态分
     */
    public BigDecimal dynamicScore(ScoreRule rule, Map<String, Object> context) {
        if (rule.dynamicBands().isEmpty()) {
            return BigDecimal.ZERO;
        }
        for (ScoreDynamicBand band : rule.dynamicBands()) {
            // 指标值缺失/无法解析时按 0 处理：此处取该区间对应指标的值进行判定
            BigDecimal value = resolveIndicatorValue(context, band.indicatorRefName());
            if (value != null && band.contains(value)) {
                return band.score() == null ? BigDecimal.ZERO : band.score();
            }
        }
        // 指标缺失或无匹配区间：动态分为 0
        return BigDecimal.ZERO;
    }

    /**
     * 从上下文解析指标值为 BigDecimal；缺失或非数值返回 null（由调用方按 0 处理）。
     */
    private BigDecimal resolveIndicatorValue(Map<String, Object> context, String indicatorRefName) {
        if (context == null || indicatorRefName == null) {
            return null;
        }
        Object raw = context.get(indicatorRefName);
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
