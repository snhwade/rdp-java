package com.riskplatform.indicator.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis 指标切片 Key 设计（design.md / Redis 指标切片 Key 设计）。
 *
 * <p>格式：{@code ind:{refName}:{dimensionKey}:{granularity}:{sliceEpochSec}}
 */
public final class SliceKey {

    public static final String PREFIX = "ind";

    private SliceKey() {
    }

    /** 构建单个切片 key。 */
    public static String of(String refName, String dimensionKey, SliceGranularity granularity, long sliceEpochSec) {
        return PREFIX + ":" + refName + ":" + dimensionKey + ":" + granularity.name() + ":" + sliceEpochSec;
    }

    /**
     * 从 {@link #of} 生成的 key 反解析组件（用于 ES 双写等场景）。
     *
     * @param key 格式 {@code ind:{refName}:{dimensionKey}:{granularity}:{sliceEpochSec}}
     */
    public static java.util.Optional<Parsed> parse(String key) {
        if (key == null || !key.startsWith(PREFIX + ":")) {
            return java.util.Optional.empty();
        }
        String body = key.substring(PREFIX.length() + 1);
        int lastColon = body.lastIndexOf(':');
        if (lastColon <= 0) {
            return java.util.Optional.empty();
        }
        String epochStr = body.substring(lastColon + 1);
        String beforeEpoch = body.substring(0, lastColon);
        int granColon = beforeEpoch.lastIndexOf(':');
        if (granColon <= 0) {
            return java.util.Optional.empty();
        }
        String granStr = beforeEpoch.substring(granColon + 1);
        String beforeGran = beforeEpoch.substring(0, granColon);
        int refColon = beforeGran.indexOf(':');
        if (refColon <= 0) {
            return java.util.Optional.empty();
        }
        String refName = beforeGran.substring(0, refColon);
        String dimensionKey = beforeGran.substring(refColon + 1);
        try {
            SliceGranularity granularity = SliceGranularity.valueOf(granStr);
            long sliceEpochSec = Long.parseLong(epochStr);
            return java.util.Optional.of(new Parsed(refName, dimensionKey, granularity, sliceEpochSec));
        } catch (Exception ex) {
            return java.util.Optional.empty();
        }
    }

    /** 切片 Key 解析结果。 */
    public record Parsed(String refName, String dimensionKey, SliceGranularity granularity, long sliceEpochSec) {
    }

    /**
     * 枚举 [now - windowDays, now] 窗口内的全部切片起点（epoch 秒）。
     *
     * @param windowDays  窗口天数
     * @param granularity 切片粒度
     * @param now         当前时刻
     * @return 升序排列的切片起点列表（含当前切片）
     */
    public static List<Long> windowSlices(int windowDays, SliceGranularity granularity, Instant now) {
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
}
