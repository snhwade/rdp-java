package com.riskplatform.ruleconfig.domain.ratingmodel;

import java.time.LocalDateTime;

/**
 * 评级模型版本快照（risk-console-redesign，R10.6/R11.5/R12.1/R13.1）。
 *
 * <p>每次评级模型创建/保存时，将该次模型整体（基础属性 + 等级区间 + 评级子项/定级项）序列化为
 * {@code snapshotJson} 并以递增的 {@code version} 写入 {@code rating_model_version} 表，
 * 供「版本历史」页签与「源码」页签（展示当前版本配置源码视图）消费。版本快照一经写入即为不可变历史记录。
 *
 * @see RatingModelVersionRepository
 */
public class RatingModelVersion {

    private Long id;
    private final Long ratingModelId;
    private final int version;
    private final String snapshotJson;
    private final String createdBy;
    private LocalDateTime createdAt;

    public RatingModelVersion(Long ratingModelId, int version, String snapshotJson, String createdBy) {
        this.ratingModelId = ratingModelId;
        this.version = version;
        this.snapshotJson = snapshotJson;
        this.createdBy = createdBy;
    }

    /** 从持久化记录重建（仓储加载用）。 */
    public static RatingModelVersion rehydrate(Long id, Long ratingModelId, int version,
                                               String snapshotJson, String createdBy, LocalDateTime createdAt) {
        RatingModelVersion v = new RatingModelVersion(ratingModelId, version, snapshotJson, createdBy);
        v.id = id;
        v.createdAt = createdAt;
        return v;
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

    public Long getRatingModelId() {
        return ratingModelId;
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
