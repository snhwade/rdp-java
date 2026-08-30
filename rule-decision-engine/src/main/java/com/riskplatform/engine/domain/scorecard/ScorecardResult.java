package com.riskplatform.engine.domain.scorecard;

import com.riskplatform.engine.domain.rule.HitDecision;

/**
 * 评分卡执行结果（S3）。
 *
 * @param totalScore  总分
 * @param level       命中的风险等级（无命中等级区间时为 null）
 * @param hitDecision 产出的命中决策（无命中等级区间时为 null，不参与聚合）
 */
public record ScorecardResult(double totalScore, String level, HitDecision hitDecision) {
}
