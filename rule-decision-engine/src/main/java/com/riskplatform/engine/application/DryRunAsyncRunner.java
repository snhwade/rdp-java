package com.riskplatform.engine.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.dryrun.DryRunJob;
import com.riskplatform.engine.domain.dryrun.DryRunJobRepository;
import com.riskplatform.engine.domain.dryrun.DryRunReport;
import com.riskplatform.engine.domain.dryrun.DryRunSample;
import com.riskplatform.engine.domain.dryrun.DryRunSampleSourcePort;
import com.riskplatform.engine.domain.dryrun.DryRunTargetPort;
import com.riskplatform.engine.domain.dryrun.DryRunTargetType;
import com.riskplatform.engine.domain.rule.HitDecision;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinition;
import com.riskplatform.engine.domain.rulepackage.RulePackageExecutor;
import com.riskplatform.engine.domain.rulepackage.RulePackageResult;
import com.riskplatform.engine.domain.rulepackage.TriggerMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 试运行异步执行器（影子模式空跑核心，R5.2–R5.6 / R14.3）。
 *
 * <p>独立 Bean 承载 {@code @Async} 执行逻辑（独立线程池 {@code dryRunExecutor}），以避免
 * 同类内自调用导致 {@code @Async} 失效的问题：{@link DryRunService} 落库 RUNNING 任务后，
 * 经本 Bean 的 Spring 代理触发异步执行，<strong>不阻塞在线决策链路</strong>（R14.3）。
 *
 * <h3>严格隔离（R5.2/R5.6）</h3>
 * 本执行器<strong>刻意不依赖</strong> {@link DecisionLogService}、{@link DecisionStrategyOutputService}、
 * {@link DecisionMetrics}：试运行<strong>不写 decision_log、不输出真实策略
 * （不写 decision_strategy_output）、不参与任何指标累计与告警</strong>，评估结果仅用于统计落
 * {@code dry_run_job}。
 *
 * <h3>命中判定约定</h3>
 * 「命中」= 至少有一条规则被触发（{@code hitRules} 非空），即 R5.2 的「触发次数」语义；命中率
 * = 触发样本数 / 样本总数。命中/评分两种模式统一适用。
 */
public class DryRunAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(DryRunAsyncRunner.class);

    /** 命中率精度（小数位）。 */
    private static final int HIT_RATE_SCALE = 6;
    /** 命中明细最大留存条数（避免报告过大）。 */
    private static final int MAX_HIT_SAMPLES = 200;
    /** 异常明细最大留存条数。 */
    private static final int MAX_ERROR_SAMPLES = 200;
    /** 评分分布分桶宽度（按总分落桶统计分布）。 */
    private static final BigDecimal SCORE_BUCKET_WIDTH = BigDecimal.TEN;

    private final DryRunJobRepository jobRepository;
    private final DryRunTargetPort targetPort;
    private final DryRunSampleSourcePort sampleSourcePort;
    private final RulePackageExecutor rulePackageExecutor;
    private final ObjectMapper objectMapper;

    public DryRunAsyncRunner(DryRunJobRepository jobRepository,
                             DryRunTargetPort targetPort,
                             DryRunSampleSourcePort sampleSourcePort,
                             RulePackageExecutor rulePackageExecutor,
                             ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.targetPort = targetPort;
        this.sampleSourcePort = sampleSourcePort;
        this.rulePackageExecutor = rulePackageExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行试运行（独立线程池，R14.3）。
     *
     * @param jobId 任务 id
     */
    @Async("dryRunExecutor")
    public void run(long jobId) {
        DryRunJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("试运行任务不存在，跳过执行: jobId={}", jobId);
            return;
        }
        try {
            RulePackageDefinition definition = targetPort.load(job.getTargetType(), job.getTargetId());
            if (definition == null) {
                job.markFailed(writeJson(Map.of("error",
                        "目标不存在或已下线: type=" + job.getTargetType() + " id=" + job.getTargetId())));
                jobRepository.update(job);
                return;
            }

            List<DryRunSample> samples = sampleSourcePort.fetch(
                    job.getSampleSource(), job.getDataFrom(), job.getDataTo(), job.getSampleLimit());

            DryRunReport report = evaluate(job.getTargetType(), definition, samples);

            job.markSuccess(report.totalCount(), report.hitCount(), report.hitRate(),
                    report.errorCount(), writeJson(report));
            jobRepository.update(job);
            log.info("试运行完成: jobId={} total={} hit={} error={}",
                    jobId, report.totalCount(), report.hitCount(), report.errorCount());
        } catch (Exception ex) {
            // 任务级异常兜底：标记 FAILED（单样本异常已在 evaluate 内隔离，不会走到这里）
            log.warn("试运行任务失败: jobId={} 原因={}", jobId, ex.getMessage());
            try {
                job.markFailed(writeJson(Map.of("error", String.valueOf(ex.getMessage()))));
                jobRepository.update(job);
            } catch (Exception ignore) {
                log.warn("试运行任务失败状态写库失败: jobId={}", jobId);
            }
        }
    }

    /**
     * 逐条评估样本并汇总报告（纯统计，无任何在线副作用）。
     */
    private DryRunReport evaluate(DryRunTargetType targetType,
                                  RulePackageDefinition definition,
                                  List<DryRunSample> samples) {
        boolean scoreMode = definition.triggerMode() == TriggerMode.SCORE;

        int total = samples.size();
        int hit = 0;
        int error = 0;
        Map<Long, Integer> ruleHitDist = new LinkedHashMap<>();
        Map<String, Integer> decisionDist = new LinkedHashMap<>();
        Map<String, Integer> scoreDist = new LinkedHashMap<>();
        Map<String, Integer> bandHitDist = new LinkedHashMap<>();
        List<DryRunReport.HitSample> hitSamples = new ArrayList<>();
        List<DryRunReport.ErrorSample> errorSamples = new ArrayList<>();

        for (DryRunSample sample : samples) {
            try {
                RulePackageResult result = rulePackageExecutor.execute(definition, sample.context());

                // 按规则命中分布（命中/触发规则）
                List<Long> hitRuleIds = new ArrayList<>();
                for (HitDecision hd : result.hitRules()) {
                    hitRuleIds.add(hd.ruleId());
                    ruleHitDist.merge(hd.ruleId(), 1, Integer::sum);
                }

                // R5.2 命中（触发）= 至少一条规则被触发
                boolean isHit = !hitRuleIds.isEmpty();
                if (isHit) {
                    hit++;
                }

                // 通用决策分布（PASS/REJECT/REVIEW），不含异常样本
                String decisionName = result.decision() == null ? "PASS" : result.decision().name();
                decisionDist.merge(decisionName, 1, Integer::sum);

                // 评分模式额外统计：总分分布 + 区间命中分布（R5.4）
                if (scoreMode) {
                    if (result.score() != null) {
                        scoreDist.merge(scoreBucketLabel(result.score()), 1, Integer::sum);
                    }
                    if (result.riskLevelCode() != null) {
                        bandHitDist.merge(result.riskLevelCode(), 1, Integer::sum);
                    }
                }

                if (isHit && hitSamples.size() < MAX_HIT_SAMPLES) {
                    hitSamples.add(new DryRunReport.HitSample(
                            sample.sampleId(),
                            decisionName,
                            result.score(),
                            result.riskLevelCode(),
                            hitRuleIds));
                }
            } catch (Exception ex) {
                // R5.5：单条样本评估异常 -> 跳过、记录原因、继续；计入 error_count
                error++;
                if (errorSamples.size() < MAX_ERROR_SAMPLES) {
                    errorSamples.add(new DryRunReport.ErrorSample(
                            sample.sampleId(), String.valueOf(ex.getMessage())));
                }
            }
        }

        BigDecimal hitRate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(hit).divide(BigDecimal.valueOf(total), HIT_RATE_SCALE, RoundingMode.HALF_UP);

        return new DryRunReport(targetType,
                definition.triggerMode().name(),
                total, hit, hitRate, error,
                ruleHitDist,
                decisionDist,
                scoreMode ? scoreDist : Map.of(),
                scoreMode ? bandHitDist : Map.of(),
                hitSamples,
                errorSamples);
    }

    /** 总分落桶标签：按 {@link #SCORE_BUCKET_WIDTH} 宽度分桶，如 "[0,10)"，兼容负分。 */
    private String scoreBucketLabel(BigDecimal score) {
        BigDecimal width = SCORE_BUCKET_WIDTH;
        BigDecimal index = score.divide(width, 0, RoundingMode.FLOOR);
        BigDecimal lower = index.multiply(width);
        BigDecimal upper = lower.add(width);
        return "[" + lower.toPlainString() + "," + upper.toPlainString() + ")";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
