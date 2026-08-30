package com.riskplatform.rating.domain;

import java.util.Map;
import java.util.TreeMap;

/**
 * 商户风险评分器（R12.1）：确定性加权计分。
 *
 * <p>评分定义：对给定的评级因子值（factorName -> 数值贡献）按加权求和，
 * 结果四舍五入并裁剪到 [0,100]。相同输入必产出相同评分（确定性）。
 *
 * <p>为保证确定性与可测性：
 * <ul>
 *   <li>因子按名称排序后累加，消除 Map 迭代顺序影响；</li>
 *   <li>缺失权重的因子按权重 0 处理（不贡献）；</li>
 *   <li>最终结果裁剪到 [0,100]，避免越界。</li>
 * </ul>
 */
public final class RatingScorer {

    private final Map<String, Double> weights;

    /**
     * @param weights 因子权重（factorName -> weight）。为保证确定性，内部复制为有序结构。
     */
    public RatingScorer(Map<String, Double> weights) {
        this.weights = new TreeMap<>(weights == null ? Map.of() : weights);
    }

    /**
     * 依据评级因子值计算 0..100 的风险评分。
     *
     * @param factorValues 因子值（factorName -> 值）
     * @return [0,100] 的整数评分
     */
    public int score(Map<String, Double> factorValues) {
        Map<String, Double> ordered = new TreeMap<>(factorValues == null ? Map.of() : factorValues);
        double raw = 0d;
        for (Map.Entry<String, Double> e : ordered.entrySet()) {
            double weight = weights.getOrDefault(e.getKey(), 0d);
            double value = e.getValue() == null ? 0d : e.getValue();
            raw += weight * value;
        }
        return clamp(Math.round(raw));
    }

    private static int clamp(long v) {
        if (v < 0) {
            return 0;
        }
        if (v > 100) {
            return 100;
        }
        return (int) v;
    }
}
