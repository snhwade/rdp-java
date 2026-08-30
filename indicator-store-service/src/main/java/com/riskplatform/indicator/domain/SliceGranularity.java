package com.riskplatform.indicator.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 指标切片粒度（R7.5 / R8.7）。
 */
public enum SliceGranularity {
    MINUTE(60L, "yyyyMMddHHmm"),
    HOUR(3600L, "yyyyMMddHH"),
    DAY(86400L, "yyyyMMdd");

    private final long stepSeconds;
    private final String pattern;

    SliceGranularity(long stepSeconds, String pattern) {
        this.stepSeconds = stepSeconds;
        this.pattern = pattern;
    }

    public long stepSeconds() {
        return stepSeconds;
    }

    public String pattern() {
        return pattern;
    }

    /** 将时刻截断到切片起点（epoch 秒，按粒度对齐）。 */
    public long truncateToSlice(Instant instant) {
        long epochSec = instant.truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        return (epochSec / stepSeconds) * stepSeconds;
    }
}
