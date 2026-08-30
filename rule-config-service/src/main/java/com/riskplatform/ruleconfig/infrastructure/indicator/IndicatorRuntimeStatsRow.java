package com.riskplatform.ruleconfig.infrastructure.indicator;

import java.time.LocalDateTime;

/** indicator_runtime_stats 查询行（MyBatis 映射）。 */
public class IndicatorRuntimeStatsRow {

    private String refName;
    private LocalDateTime lastAccumulateAt;
    private long readMissCount;

    public String getRefName() {
        return refName;
    }

    public void setRefName(String refName) {
        this.refName = refName;
    }

    public LocalDateTime getLastAccumulateAt() {
        return lastAccumulateAt;
    }

    public void setLastAccumulateAt(LocalDateTime lastAccumulateAt) {
        this.lastAccumulateAt = lastAccumulateAt;
    }

    public long getReadMissCount() {
        return readMissCount;
    }

    public void setReadMissCount(long readMissCount) {
        this.readMissCount = readMissCount;
    }
}
