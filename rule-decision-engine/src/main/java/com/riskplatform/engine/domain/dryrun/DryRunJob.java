package com.riskplatform.engine.domain.dryrun;

import java.time.LocalDateTime;

/**
 * 试运行任务（dryrun 聚合根，对应 dry_run_job 表，R5.1/R5.3）。
 *
 * <p>承载一次试运行的发起参数、运行状态与统计结果。报告明细另由 {@link DryRunReport} 承载并以
 * JSON 落 {@code report_json}。聚合提供 {@link #markSuccess}/{@link #markFailed} 状态流转方法，
 * 保证状态从 RUNNING 单向流转到终态（SUCCESS/FAILED）。
 */
public class DryRunJob {

    private Long id;
    private final DryRunTargetType targetType;
    private final long targetId;
    private final DryRunSampleSource sampleSource;
    private final LocalDateTime dataFrom;
    private final LocalDateTime dataTo;
    private final int sampleLimit;
    private final String createdBy;

    private DryRunStatus status;
    private int totalCount;
    private int hitCount;
    private java.math.BigDecimal hitRate;
    private int errorCount;
    private String reportJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /**
     * 新建一个 RUNNING 状态的试运行任务。
     */
    public DryRunJob(DryRunTargetType targetType,
                     long targetId,
                     DryRunSampleSource sampleSource,
                     LocalDateTime dataFrom,
                     LocalDateTime dataTo,
                     int sampleLimit,
                     String createdBy) {
        if (targetType == null) {
            throw new IllegalArgumentException("目标类型不能为空");
        }
        if (sampleSource == null) {
            throw new IllegalArgumentException("样本来源不能为空");
        }
        this.targetType = targetType;
        this.targetId = targetId;
        this.sampleSource = sampleSource;
        this.dataFrom = dataFrom;
        this.dataTo = dataTo;
        this.sampleLimit = Math.max(sampleLimit, 0);
        this.createdBy = createdBy;
        this.status = DryRunStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    /** 重建已有任务（仓储读取用）。 */
    public static DryRunJob rehydrate(Long id, DryRunTargetType targetType, long targetId,
                                      DryRunSampleSource sampleSource, LocalDateTime dataFrom,
                                      LocalDateTime dataTo, int sampleLimit, DryRunStatus status,
                                      int totalCount, int hitCount, java.math.BigDecimal hitRate,
                                      int errorCount, String reportJson, String createdBy,
                                      LocalDateTime startedAt, LocalDateTime finishedAt) {
        DryRunJob job = new DryRunJob(targetType, targetId, sampleSource, dataFrom, dataTo,
                sampleLimit, createdBy);
        job.id = id;
        job.status = status;
        job.totalCount = totalCount;
        job.hitCount = hitCount;
        job.hitRate = hitRate;
        job.errorCount = errorCount;
        job.reportJson = reportJson;
        job.startedAt = startedAt;
        job.finishedAt = finishedAt;
        return job;
    }

    /**
     * 标记任务成功并写入统计结果（R5.3）。
     *
     * @param totalCount 样本总数
     * @param hitCount   命中数
     * @param hitRate    命中率
     * @param errorCount 异常样本数
     * @param reportJson 报告 JSON（分布/明细摘要）
     */
    public void markSuccess(int totalCount, int hitCount, java.math.BigDecimal hitRate,
                            int errorCount, String reportJson) {
        this.status = DryRunStatus.SUCCESS;
        this.totalCount = totalCount;
        this.hitCount = hitCount;
        this.hitRate = hitRate;
        this.errorCount = errorCount;
        this.reportJson = reportJson;
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 标记任务失败（任务级异常，R5.5 任务级兜底）。
     *
     * @param reasonJson 失败原因（JSON 摘要）
     */
    public void markFailed(String reasonJson) {
        this.status = DryRunStatus.FAILED;
        this.reportJson = reasonJson;
        this.finishedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public DryRunTargetType getTargetType() {
        return targetType;
    }

    public long getTargetId() {
        return targetId;
    }

    public DryRunSampleSource getSampleSource() {
        return sampleSource;
    }

    public LocalDateTime getDataFrom() {
        return dataFrom;
    }

    public LocalDateTime getDataTo() {
        return dataTo;
    }

    public int getSampleLimit() {
        return sampleLimit;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public DryRunStatus getStatus() {
        return status;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getHitCount() {
        return hitCount;
    }

    public java.math.BigDecimal getHitRate() {
        return hitRate;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public String getReportJson() {
        return reportJson;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
}
