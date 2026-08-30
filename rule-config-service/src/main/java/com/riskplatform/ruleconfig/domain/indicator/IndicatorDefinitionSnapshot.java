package com.riskplatform.ruleconfig.domain.indicator;

import java.time.Instant;

/** 指标定义快照（IV1）：每次更新前写入一条，用于回退到上一版定义。 */
public class IndicatorDefinitionSnapshot {

    private Long id;
    private final Long indicatorDefinitionId;
    private final int version;
    private final String snapshotJson;
    private final String createdBy;
    private Instant createdAt;

    public IndicatorDefinitionSnapshot(Long indicatorDefinitionId, int version,
                                       String snapshotJson, String createdBy) {
        this.indicatorDefinitionId = indicatorDefinitionId;
        this.version = version;
        this.snapshotJson = snapshotJson;
        this.createdBy = createdBy;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public void assignCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getIndicatorDefinitionId() {
        return indicatorDefinitionId;
    }

    public int getVersion() {
        return version;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
