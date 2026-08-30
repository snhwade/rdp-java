package com.riskplatform.indicator.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 切片读写与聚合单元测试（R9.1/R9.2/R9.3，含窗口老化 R8.7）。
 */
class RedisSliceAggregatorTest {

    @Test
    void sliceKey_format() {
        String key = SliceKey.of("txn_cnt_7d", "merchant#M001", SliceGranularity.HOUR, 1_700_000_000L);
        assertThat(key).isEqualTo("ind:txn_cnt_7d:merchant#M001:HOUR:1700000000");
    }

    @Test
    void windowSlices_coversWindowInclusive() {
        Instant now = Instant.parse("2024-01-02T00:00:00Z");
        List<Long> slices = SliceKey.windowSlices(1, SliceGranularity.HOUR, now);
        // 1 天窗口、小时粒度：25 个切片起点（含两端对齐）
        assertThat(slices).hasSize(25);
        assertThat(slices).isSorted();
    }

    @Test
    void aggregate_sumsAllSlicesInWindow() {
        InMemorySliceStore store = new InMemorySliceStore();
        RedisSliceAggregator agg = new RedisSliceAggregator(store);
        Instant now = Instant.parse("2024-01-02T12:00:00Z");
        store.setNow(now.getEpochSecond());

        // 在窗口内写入两个不同小时切片
        agg.writeSlice("ind1", "m#1", SliceGranularity.HOUR, now, 3.0, 7);
        agg.writeSlice("ind1", "m#1", SliceGranularity.HOUR, now.minusSeconds(3600), 5.0, 7);

        double total = agg.aggregate("ind1", "m#1", 7, SliceGranularity.HOUR, now);
        assertThat(total).isEqualTo(8.0);
    }

    @Test
    void windowAging_expiredSliceExcludedFromCurrentValue() {
        InMemorySliceStore store = new InMemorySliceStore();
        RedisSliceAggregator agg = new RedisSliceAggregator(store);

        // t0 写入一个 1 天窗口的切片（TTL = 1 天 + 1 小时）
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        store.setNow(t0.getEpochSecond());
        agg.writeSlice("ind1", "m#1", SliceGranularity.HOUR, t0, 10.0, 1);

        // 推进逻辑时间超过 TTL（2 天后），该历史切片应过期、不参与当前值
        Instant later = t0.plusSeconds(2 * 86400L);
        store.setNow(later.getEpochSecond());
        double total = agg.aggregate("ind1", "m#1", 1, SliceGranularity.HOUR, later);
        assertThat(total).isEqualTo(0.0);
    }

    @Test
    void dedup_firstTrueThenFalse() {
        InMemorySliceStore store = new InMemorySliceStore();
        store.setNow(1000L);
        assertThat(store.markProcessedIfAbsent("order#1", 600)).isTrue();
        assertThat(store.markProcessedIfAbsent("order#1", 600)).isFalse();
    }
}
