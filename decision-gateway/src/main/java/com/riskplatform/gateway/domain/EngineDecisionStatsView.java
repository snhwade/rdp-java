package com.riskplatform.gateway.domain;

import java.util.List;
import java.util.Map;

/** 引擎调用时段统计（XS1）。 */
public record EngineDecisionStatsView(
        long total,
        Map<String, Long> decisionDistribution,
        double avgElapsedMs,
        long p99ElapsedMs,
        List<EventTypeBucket> byEventType) {

    public record EventTypeBucket(String eventTypeCode, long total) {
    }
}
