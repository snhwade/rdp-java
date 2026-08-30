package com.riskplatform.engine.application;

import com.riskplatform.engine.domain.dryrun.DryRunJob;
import com.riskplatform.engine.domain.dryrun.DryRunJobRepository;
import com.riskplatform.engine.domain.dryrun.DryRunSampleSource;
import com.riskplatform.engine.domain.dryrun.DryRunTargetType;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 试运行服务（影子模式发起入口，R5.1–R5.6 / R14.3）。
 *
 * <p>负责试运行任务的发起与查询：
 * <ul>
 *   <li>{@link #start} 同步落库 RUNNING 任务并触发异步执行，立即返回 jobId（不阻塞调用方）；</li>
 *   <li>{@link #query} 查询任务状态与报告。</li>
 * </ul>
 *
 * <p>异步空跑评估逻辑委托给独立 Bean {@link DryRunAsyncRunner}（{@code @Async} 独立线程池），
 * 以保证 {@code @Async} 经 Spring 代理生效、不阻塞在线决策链路（R14.3）。
 *
 * <h3>严格隔离（R5.2/R5.6）</h3>
 * 本服务与 {@link DryRunAsyncRunner} 均<strong>不依赖</strong> {@link DecisionLogService}、
 * {@link DecisionStrategyOutputService}、{@link DecisionMetrics}，因此试运行<strong>不写
 * decision_log、不输出真实策略、不参与任何指标累计与告警</strong>。
 */
public class DryRunService {

    private final DryRunJobRepository jobRepository;
    private final DryRunAsyncRunner asyncRunner;

    public DryRunService(DryRunJobRepository jobRepository, DryRunAsyncRunner asyncRunner) {
        this.jobRepository = jobRepository;
        this.asyncRunner = asyncRunner;
    }

    /**
     * 发起试运行：同步落库 RUNNING 任务并触发异步执行，立即返回 jobId（R5.1）。
     *
     * @param command 发起参数
     * @return 任务 id（状态 RUNNING）
     */
    public Long start(StartCommand command) {
        DryRunJob job = new DryRunJob(
                command.targetType(), command.targetId(), command.sampleSource(),
                command.dataFrom(), command.dataTo(), command.sampleLimit(), command.createdBy());
        Long jobId = jobRepository.save(job);
        // 异步执行（独立线程池，不阻塞在线链路 R14.3）。经独立 Bean 的 Spring 代理使 @Async 生效。
        asyncRunner.run(jobId);
        return jobId;
    }

    /**
     * 查询试运行任务（含状态与报告 JSON，R5.3）。
     *
     * @param jobId 任务 id
     * @return 任务（不存在时为空）
     */
    public Optional<DryRunJob> query(long jobId) {
        return jobRepository.findById(jobId);
    }

    /**
     * 发起试运行命令（R5.1）。
     *
     * @param targetType   目标类型（RULE/RULE_PACKAGE）
     * @param targetId     目标 id
     * @param sampleSource 样本来源（ORDER/EVENT）
     * @param dataFrom     样本起始时间（可空）
     * @param dataTo       样本结束时间（可空）
     * @param sampleLimit  样本数量上限（&lt;=0 表示不限）
     * @param createdBy    发起人
     */
    public record StartCommand(DryRunTargetType targetType,
                               long targetId,
                               DryRunSampleSource sampleSource,
                               LocalDateTime dataFrom,
                               LocalDateTime dataTo,
                               int sampleLimit,
                               String createdBy) {
    }
}
