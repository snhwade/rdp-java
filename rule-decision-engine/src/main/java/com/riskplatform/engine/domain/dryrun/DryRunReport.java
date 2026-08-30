package com.riskplatform.engine.domain.dryrun;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 试运行报告（R5.3/R5.4/R5.5）。
 *
 * <p>试运行完成后产出的统计摘要，序列化为 JSON 落 {@code dry_run_job.report_json}：
 * <ul>
 *   <li>样本总数 / 命中数 / 命中率 / 异常样本数（任务表列同步存储，报告内再冗余一份便于下钻）；</li>
 *   <li>按规则维度的命中分布 {@link #ruleHitDistribution}（ruleId → 命中次数）；</li>
 *   <li>决策分布 {@link #decisionDistribution}（PASS/REJECT/REVIEW → 样本数，通用统计）；</li>
 *   <li>命中样本明细 {@link #hitSamples}（可下钻，限量摘要）；</li>
 *   <li>评分包额外：总分分布 {@link #scoreDistribution}（分值桶 → 数量）与
 *       各分值区间命中数量 {@link #bandHitDistribution}（风险等级编码 → 命中次数，R5.4）；</li>
 *   <li>异常样本原因摘要 {@link #errorSamples}（R5.5）。</li>
 * </ul>
 *
 * @param targetType         目标类型
 * @param triggerMode        触发模式（HIT/SCORE，便于报告消费方区分评分分布是否有意义）
 * @param totalCount         样本总数
 * @param hitCount           命中数（目标决策非 PASS 视为命中，见 DryRunService 约定）
 * @param hitRate            命中率（hitCount/totalCount；total=0 时为 0）
 * @param errorCount         异常样本数（被隔离）
 * @param ruleHitDistribution 按规则命中分布（ruleId → 命中次数）
 * @param decisionDistribution 决策分布（PASS/REJECT/REVIEW → 样本数；异常样本不计入）
 * @param scoreDistribution  评分模式总分分布（分值桶标签 → 数量；命中模式为空）
 * @param bandHitDistribution 评分模式分值区间命中分布（风险等级编码 → 命中次数；命中模式为空）
 * @param hitSamples         命中样本明细摘要（可下钻，限量）
 * @param errorSamples       异常样本摘要（sampleId + 原因，限量）
 */
public record DryRunReport(DryRunTargetType targetType,
                           String triggerMode,
                           int totalCount,
                           int hitCount,
                           BigDecimal hitRate,
                           int errorCount,
                           Map<Long, Integer> ruleHitDistribution,
                           Map<String, Integer> decisionDistribution,
                           Map<String, Integer> scoreDistribution,
                           Map<String, Integer> bandHitDistribution,
                           List<HitSample> hitSamples,
                           List<ErrorSample> errorSamples) {

    public DryRunReport {
        ruleHitDistribution = ruleHitDistribution == null ? Map.of() : Map.copyOf(ruleHitDistribution);
        decisionDistribution = decisionDistribution == null ? Map.of() : Map.copyOf(decisionDistribution);
        scoreDistribution = scoreDistribution == null ? Map.of() : Map.copyOf(scoreDistribution);
        bandHitDistribution = bandHitDistribution == null ? Map.of() : Map.copyOf(bandHitDistribution);
        hitSamples = hitSamples == null ? List.of() : List.copyOf(hitSamples);
        errorSamples = errorSamples == null ? List.of() : List.copyOf(errorSamples);
    }

    /**
     * 命中样本明细（可下钻，R5.3）。
     *
     * @param sampleId      样本标识（订单 eventId）
     * @param decision      该样本目标评估得到的决策
     * @param score         评分模式总分（命中模式为 null）
     * @param riskLevelCode 评分模式命中区间风险等级（命中模式为 null）
     * @param hitRuleIds    命中/触发的规则 id 列表
     */
    public record HitSample(String sampleId,
                            String decision,
                            BigDecimal score,
                            String riskLevelCode,
                            List<Long> hitRuleIds) {
    }

    /**
     * 异常样本摘要（R5.5）。
     *
     * @param sampleId 样本标识
     * @param reason   异常原因（被隔离跳过）
     */
    public record ErrorSample(String sampleId, String reason) {
    }
}
