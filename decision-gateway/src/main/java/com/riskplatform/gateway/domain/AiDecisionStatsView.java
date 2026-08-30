package com.riskplatform.gateway.domain;

import java.util.List;

/**
 * AI 决策运营统计（T3 分歧 + IS 运行通用统计）。
 */
public record AiDecisionStatsView(
        long total,
        long success,
        long failed,
        long pending,
        long timedOut,
        long divergenceCount,
        double divergenceRate,
        double failRate,
        List<EventTypeBucket> byEventType,
        List<AdoptionModeBucket> byAdoptionMode,
        long modelScoreCalls,
        long modelScoreAvailable,
        double modelScoreAvailableRate) {

    public record EventTypeBucket(String eventTypeCode, long total, long divergenceCount) {
    }

    public record AdoptionModeBucket(String adoptionMode, long total) {
    }
}
