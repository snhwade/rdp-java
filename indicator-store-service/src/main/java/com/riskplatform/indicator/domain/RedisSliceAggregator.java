package com.riskplatform.indicator.domain;

import java.time.Instant;
import java.util.List;

/**
 * Redis 切片聚合器（R9.2/R9.3 / R8.7）。
 *
 * <p>读取 [now-window, now] 窗口内全部切片并求和聚合，得到指标当前值。
 * 超出窗口的历史切片因 TTL 过期而不在范围内，不参与当前值计算（窗口老化）。
 */
public class RedisSliceAggregator {

    private final SliceStore sliceStore;

    public RedisSliceAggregator(SliceStore sliceStore) {
        this.sliceStore = sliceStore;
    }

    /**
     * 聚合指定指标在给定维度上的窗口当前值。
     *
     * @param refName      指标引用名
     * @param dimensionKey 维度键
     * @param windowDays   窗口天数
     * @param granularity  切片粒度
     * @param now          当前时刻
     * @return 窗口内所有切片值之和
     */
    public double aggregate(String refName, String dimensionKey, int windowDays,
                            SliceGranularity granularity, Instant now) {
        List<Long> slices = SliceKey.windowSlices(windowDays, granularity, now);
        List<String> keys = slices.stream()
                .map(ts -> SliceKey.of(refName, dimensionKey, granularity, ts))
                .toList();
        return sliceStore.readSlices(keys).stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * 写入某切片的累计值，TTL 取窗口长度 + 一个切片宽度的缓冲（自动老化 R8.7）。
     */
    public void writeSlice(String refName, String dimensionKey, SliceGranularity granularity,
                           Instant eventTime, double value, int windowDays) {
        long sliceTs = granularity.truncateToSlice(eventTime);
        String key = SliceKey.of(refName, dimensionKey, granularity, sliceTs);
        long ttl = (long) windowDays * 86400L + granularity.stepSeconds();
        sliceStore.writeSlice(key, value, ttl);
    }
}
