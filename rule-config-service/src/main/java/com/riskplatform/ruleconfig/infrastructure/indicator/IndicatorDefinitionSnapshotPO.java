package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("indicator_definition_snapshot")
public class IndicatorDefinitionSnapshotPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long indicatorDefinitionId;
    private Integer version;
    private String snapshotJson;
    private String createdBy;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIndicatorDefinitionId() { return indicatorDefinitionId; }
    public void setIndicatorDefinitionId(Long indicatorDefinitionId) { this.indicatorDefinitionId = indicatorDefinitionId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
