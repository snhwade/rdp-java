package com.riskplatform.indicator.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 测试用内存切片存储：模拟 Redis 行为，支持 TTL 过期（按"逻辑当前时刻"判定）。
 */
public class InMemorySliceStore implements SliceStore {

    private static final class Entry {
        double value;
        long expireAtEpochSec; // Long.MAX_VALUE 表示不过期
    }

    private final Map<String, Entry> store = new HashMap<>();
    private final Map<String, Long> dedup = new HashMap<>();
    private long nowEpochSec = 0L;

    /** 设置逻辑当前时刻（用于 TTL 过期判定）。 */
    public void setNow(long epochSec) {
        this.nowEpochSec = epochSec;
    }

    @Override
    public void writeSlice(String key, double value, long ttlSeconds) {
        Entry e = new Entry();
        e.value = value;
        e.expireAtEpochSec = nowEpochSec + ttlSeconds;
        store.put(key, e);
    }

    @Override
    public void incrementSlice(String key, double increment, long ttlSeconds) {
        Entry e = store.get(key);
        if (e == null || e.expireAtEpochSec <= nowEpochSec) {
            e = new Entry();
            e.value = 0d;
        }
        e.value += increment;
        e.expireAtEpochSec = nowEpochSec + ttlSeconds;
        store.put(key, e);
    }

    @Override
    public Optional<Double> readSlice(String key) {
        Entry e = store.get(key);
        if (e == null || e.expireAtEpochSec <= nowEpochSec) {
            return Optional.empty();
        }
        return Optional.of(e.value);
    }

    @Override
    public List<Double> readSlices(List<String> keys) {
        List<Double> result = new ArrayList<>();
        for (String k : keys) {
            readSlice(k).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public boolean markProcessedIfAbsent(String dedupKey, long ttlSeconds) {
        Long exp = dedup.get(dedupKey);
        if (exp != null && exp > nowEpochSec) {
            return false;
        }
        dedup.put(dedupKey, nowEpochSec + ttlSeconds);
        return true;
    }
}
