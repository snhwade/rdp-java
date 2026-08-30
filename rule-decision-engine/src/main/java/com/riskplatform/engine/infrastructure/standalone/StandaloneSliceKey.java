package com.riskplatform.engine.infrastructure.standalone;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Redis 指标切片 Key（与 indicator-store {@code SliceKey} 格式一致）。 */
final class StandaloneSliceKey {

    private static final String PREFIX = "ind";

    private StandaloneSliceKey() {
    }

    static String of(String refName, String dimensionKey, StandaloneSliceGranularity granularity, long sliceEpochSec) {
        return PREFIX + ":" + refName + ":" + dimensionKey + ":" + granularity.name() + ":" + sliceEpochSec;
    }

    static List<Long> windowSlices(int windowDays, StandaloneSliceGranularity granularity, Instant now) {
        long step = granularity.stepSeconds();
        long end = granularity.truncateToSlice(now);
        long windowSeconds = (long) windowDays * 86400L;
        long start = granularity.truncateToSlice(now.minusSeconds(windowSeconds));
        List<Long> slices = new ArrayList<>();
        for (long t = start; t <= end; t += step) {
            slices.add(t);
        }
        return slices;
    }

    enum StandaloneSliceGranularity {
        MINUTE(60L),
        HOUR(3600L),
        DAY(86400L);

        private final long stepSeconds;

        StandaloneSliceGranularity(long stepSeconds) {
            this.stepSeconds = stepSeconds;
        }

        long stepSeconds() {
            return stepSeconds;
        }

        long truncateToSlice(Instant instant) {
            long epochSec = instant.truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
            return (epochSec / stepSeconds) * stepSeconds;
        }
    }
}
