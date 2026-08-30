package com.riskplatform.indicator.application;

import com.riskplatform.indicator.domain.EsStore;
import com.riskplatform.indicator.domain.RedisUnavailableException;
import com.riskplatform.indicator.domain.SliceGranularity;
import com.riskplatform.indicator.domain.SliceKey;
import com.riskplatform.indicator.domain.SliceStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndicatorStorageWriterTest {

    @Test
    void applySliceIncrement_dualWrite() {
        AtomicBoolean redisIncremented = new AtomicBoolean(false);
        AtomicBoolean esWritten = new AtomicBoolean(false);
        SliceStore redis = new SliceStore() {
            @Override
            public void writeSlice(String key, double value, long ttlSeconds) {
            }

            @Override
            public void incrementSlice(String key, double increment, long ttlSeconds) {
                redisIncremented.set(true);
            }

            @Override
            public Optional<Double> readSlice(String key) {
                return Optional.empty();
            }

            @Override
            public List<Double> readSlices(List<String> keys) {
                return List.of();
            }

            @Override
            public boolean markProcessedIfAbsent(String dedupKey, long ttlSeconds) {
                return true;
            }
        };
        EsStore es = new EsStore() {
            @Override
            public void write(String refName, String dimensionKey, long sliceTs, double value, String orderId) {
                esWritten.set(true);
            }

            @Override
            public Optional<Double> readSlice(String refName, String dimensionKey, long sliceTs) {
                return Optional.of(10.0);
            }

            @Override
            public Optional<Double> readWindow(String refName, String dimensionKey, int windowDays,
                                               SliceGranularity granularity, java.time.Instant now) {
                return Optional.empty();
            }
        };
        StorageProperties props = new StorageProperties();
        props.setWriteRedis(true);
        props.setWriteEs(true);
        IndicatorStorageWriter writer = new IndicatorStorageWriter(props, redis, es);
        writer.applySliceIncrement(new com.riskplatform.common.model.IndicatorSliceUpdate(
                "txn_cnt", "M001", "DAY", 1704067200L, 5.0, "o1",
                "ind:txn_cnt:M001:DAY:1704067200", 86400L));
        assertThat(redisIncremented).isTrue();
        assertThat(esWritten).isTrue();
    }

    @Test
    void dualWriteWritesBothWhenEnabled() {
        AtomicBoolean redisWritten = new AtomicBoolean(false);
        AtomicBoolean esWritten = new AtomicBoolean(false);
        SliceStore redis = stubRedis(redisWritten);
        EsStore es = stubEs(esWritten);
        StorageProperties props = new StorageProperties();
        props.setWriteRedis(true);
        props.setWriteEs(true);

        IndicatorStorageWriter writer = new IndicatorStorageWriter(props, redis, es);
        String key = SliceKey.of("b2b_daily_amt", "M001", SliceGranularity.DAY, 1704067200L);
        writer.writeSlice(key, 100.0, 86400L, "order-1");

        assertThat(redisWritten).isTrue();
        assertThat(esWritten).isTrue();
    }

    @Test
    void writeEsOnlySkipsRedis() {
        AtomicBoolean redisWritten = new AtomicBoolean(false);
        AtomicBoolean esWritten = new AtomicBoolean(false);
        StorageProperties props = new StorageProperties();
        props.setWriteRedis(false);
        props.setWriteEs(true);

        IndicatorStorageWriter writer = new IndicatorStorageWriter(props,
                stubRedis(redisWritten), stubEs(esWritten));
        String key = SliceKey.of("ai_fraud_score", "M002", SliceGranularity.DAY, 1704067200L);
        writer.writeSlice(key, 0.9, 86400L, "API");

        assertThat(redisWritten).isFalse();
        assertThat(esWritten).isTrue();
    }

    @Test
    void partialFailureWhenOneTargetSucceeds() {
        StorageProperties props = new StorageProperties();
        props.setWriteRedis(true);
        props.setWriteEs(true);
        SliceStore redis = new SliceStore() {
            @Override
            public void writeSlice(String key, double value, long ttlSeconds) {
                throw new RedisUnavailableException("down");
            }

            @Override
            public void incrementSlice(String key, double increment, long ttlSeconds) {
                throw new RedisUnavailableException("down");
            }

            @Override
            public Optional<Double> readSlice(String key) {
                return Optional.empty();
            }

            @Override
            public List<Double> readSlices(List<String> keys) {
                return List.of();
            }

            @Override
            public boolean markProcessedIfAbsent(String dedupKey, long ttlSeconds) {
                return true;
            }
        };
        AtomicBoolean esWritten = new AtomicBoolean(false);
        IndicatorStorageWriter writer = new IndicatorStorageWriter(props, redis, stubEs(esWritten));
        String key = SliceKey.of("txn_cnt_1d", "M003", SliceGranularity.DAY, 1704067200L);
        writer.writeSlice(key, 1.0, 86400L, "o1");
        assertThat(esWritten).isTrue();
    }

    @Test
    void noTargetConfiguredThrows() {
        StorageProperties props = new StorageProperties();
        props.setWriteRedis(false);
        props.setWriteEs(false);
        IndicatorStorageWriter writer = new IndicatorStorageWriter(props, stubRedis(new AtomicBoolean()),
                stubEs(new AtomicBoolean()));
        assertThatThrownBy(() -> writer.writeSlice("ind:x:y:DAY:1", 1.0, 60L, "o"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static SliceStore stubRedis(AtomicBoolean flag) {
        return new SliceStore() {
            @Override
            public void writeSlice(String key, double value, long ttlSeconds) {
                flag.set(true);
            }

            @Override
            public void incrementSlice(String key, double increment, long ttlSeconds) {
                flag.set(true);
            }

            @Override
            public Optional<Double> readSlice(String key) {
                return Optional.empty();
            }

            @Override
            public List<Double> readSlices(List<String> keys) {
                return List.of();
            }

            @Override
            public boolean markProcessedIfAbsent(String dedupKey, long ttlSeconds) {
                return true;
            }
        };
    }

    private static EsStore stubEs(AtomicBoolean flag) {
        return new EsStore() {
            @Override
            public void write(String refName, String dimensionKey, long sliceTs, double value, String orderId) {
                flag.set(true);
            }

            @Override
            public Optional<Double> readSlice(String refName, String dimensionKey, long sliceTs) {
                return Optional.empty();
            }

            @Override
            public Optional<Double> readWindow(String refName, String dimensionKey, int windowDays,
                                               SliceGranularity granularity, java.time.Instant now) {
                return Optional.empty();
            }
        };
    }
}
