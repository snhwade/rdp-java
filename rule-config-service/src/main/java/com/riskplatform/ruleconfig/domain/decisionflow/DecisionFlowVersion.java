package com.riskplatform.ruleconfig.domain.decisionflow;

import java.time.LocalDateTime;

/**
 * 决策流版本快照（扩展阶段，R6.5）。
 *
 * <p>每次决策流创建/更新时，将该次决策流整体（节点/边/归属维度等）序列化为
 * {@code snapshotJson} 并以递增的 {@code version} 写入 {@code decision_flow_version} 表，
 * 供「版本列表」与「两版本差异对比」使用。版本快照一经写入即为不可变历史记录。
 *
 * @see DecisionFlowVersionRepository
 */
public class DecisionFlowVersion {

    /** 版本上下线状态：已上线。 */
    public static final String STATUS_ONLINE = "ONLINE";
    /** 版本上下线状态：已下线（默认）。 */
    public static final String STATUS_OFFLINE = "OFFLINE";

    private Long id;
    private final Long decisionFlowId;
    private final int version;
    private final String snapshotJson;
    private final String createdBy;
    private LocalDateTime createdAt;
    /** 版本上下线状态（ONLINE/OFFLINE），新建快照默认 OFFLINE（R8.5/R8.6/R8.7）。 */
    private String status = STATUS_OFFLINE;

    public DecisionFlowVersion(Long decisionFlowId, int version, String snapshotJson, String createdBy) {
        this.decisionFlowId = decisionFlowId;
        this.version = version;
        this.snapshotJson = snapshotJson;
        this.createdBy = createdBy;
    }

    /** 从持久化记录重建（仓储加载用）。 */
    public static DecisionFlowVersion rehydrate(Long id, Long decisionFlowId, int version,
                                                String snapshotJson, String createdBy, LocalDateTime createdAt) {
        return rehydrate(id, decisionFlowId, version, snapshotJson, createdBy, createdAt, STATUS_OFFLINE);
    }

    /** 从持久化记录重建（含上下线状态，仓储加载用）。 */
    public static DecisionFlowVersion rehydrate(Long id, Long decisionFlowId, int version,
                                                String snapshotJson, String createdBy,
                                                LocalDateTime createdAt, String status) {
        DecisionFlowVersion v = new DecisionFlowVersion(decisionFlowId, version, snapshotJson, createdBy);
        v.id = id;
        v.createdAt = createdAt;
        v.status = status == null ? STATUS_OFFLINE : status;
        return v;
    }

    /** 置为已上线。 */
    public void online() {
        this.status = STATUS_ONLINE;
    }

    /** 置为已下线。 */
    public void offline() {
        this.status = STATUS_OFFLINE;
    }

    /** 是否已上线。 */
    public boolean isOnline() {
        return STATUS_ONLINE.equals(status);
    }

    public String getStatus() {
        return status;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public void assignCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getDecisionFlowId() {
        return decisionFlowId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
