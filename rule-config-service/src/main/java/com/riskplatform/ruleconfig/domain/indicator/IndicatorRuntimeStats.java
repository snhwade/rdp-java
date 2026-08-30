package com.riskplatform.ruleconfig.domain.indicator;

import java.time.Instant;

/** 指标运行统计（IS1）。 */
public record IndicatorRuntimeStats(
        String refName,
        Instant lastAccumulateAt,
        long readMissCount) {
}
