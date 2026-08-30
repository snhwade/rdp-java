package com.riskplatform.engine.domain.score;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评分规则（引擎执行侧轻量 DTO，R4.1/R4.2）。
 *
 * <p>一条已触发的评分规则的评分配置：基础分 {@link #baseScore}（可为负）
 * + 动态分区间列表 {@link #dynamicBands}（数值型指标按取值分段得分）。
 *
 * <p>本 DTO 仅承载评分计算所需数据，便于纯函数计算与属性测试；不含状态。
 *
 * @param ruleId       规则 id（来源标识，用于追溯）
 * @param baseScore    基础分（可为负；null 视为 0）
 * @param dynamicBands 动态分区间列表（可空/可为空列表，表示无动态分）
 */
public record ScoreRule(Long ruleId,
                        BigDecimal baseScore,
                        List<ScoreDynamicBand> dynamicBands) {

    public ScoreRule {
        dynamicBands = dynamicBands == null ? List.of() : List.copyOf(dynamicBands);
    }
}
