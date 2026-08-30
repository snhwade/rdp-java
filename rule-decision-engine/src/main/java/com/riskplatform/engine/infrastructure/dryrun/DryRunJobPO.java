package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试运行任务持久化对象（对应 dry_run_job 表，V17，R5.1/R5.3）。
 *
 * <p>{@code report_json} 存试运行报告（总分分布/区间命中/明细摘要）。本表由 rule-config-service
 * 的 Flyway V17 迁移创建，引擎与配置服务共享同一 MySQL 库（与 decision_log 同库）。
 */
@TableName("dry_run_job")
public class DryRunJobPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String targetType;
    private Long targetId;
    private String sampleSource;
    private LocalDateTime dataFrom;
    private LocalDateTime dataTo;
    private Integer sampleLimit;
    private String status;
    private Integer totalCount;
    private Integer hitCount;
    private BigDecimal hitRate;
    private Integer errorCount;
    private String reportJson;
    private String createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getSampleSource() {
        return sampleSource;
    }

    public void setSampleSource(String sampleSource) {
        this.sampleSource = sampleSource;
    }

    public LocalDateTime getDataFrom() {
        return dataFrom;
    }

    public void setDataFrom(LocalDateTime dataFrom) {
        this.dataFrom = dataFrom;
    }

    public LocalDateTime getDataTo() {
        return dataTo;
    }

    public void setDataTo(LocalDateTime dataTo) {
        this.dataTo = dataTo;
    }

    public Integer getSampleLimit() {
        return sampleLimit;
    }

    public void setSampleLimit(Integer sampleLimit) {
        this.sampleLimit = sampleLimit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public BigDecimal getHitRate() {
        return hitRate;
    }

    public void setHitRate(BigDecimal hitRate) {
        this.hitRate = hitRate;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
