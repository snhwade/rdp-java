package com.riskplatform.ruleconfig.domain.rulepackage;

import java.time.Instant;

/**
 * 规则包启用快照（P2）：每次规则包启用时写入一条，用于回退到上一启用状态。
 */
public class RulePackageEnabledSnapshot {

    private Long id;
    private final Long rulePackageId;
    private final int version;
    private final String snapshotJson;
    private final String createdBy;
    private Instant createdAt;

    public RulePackageEnabledSnapshot(Long rulePackageId, int version, String snapshotJson, String createdBy) {
        this.rulePackageId = rulePackageId;
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

    public Long getRulePackageId() {
        return rulePackageId;
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
