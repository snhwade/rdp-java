package com.riskplatform.indicator.application;

import com.riskplatform.common.model.IndicatorSliceUpdate;
import com.riskplatform.indicator.domain.EsStore;
import com.riskplatform.indicator.domain.RedisUnavailableException;
import com.riskplatform.indicator.domain.SliceKey;
import com.riskplatform.indicator.domain.SliceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 指标切片写入路由：按配置写入 Redis / ES（可双写）。
 */
public class IndicatorStorageWriter {

    private static final Logger log = LoggerFactory.getLogger(IndicatorStorageWriter.class);

    private final StorageProperties storage;
    private final SliceStore redisStore;
    private final EsStore esStore;

    public IndicatorStorageWriter(StorageProperties storage, SliceStore redisStore, EsStore esStore) {
        this.storage = storage;
        this.redisStore = redisStore;
        this.esStore = esStore;
    }

    /**
     * 应用 Flink 下发的切片增量事件（INCRBYFLOAT 语义）。
     */
    public void applySliceIncrement(IndicatorSliceUpdate update) {
        if (update == null || update.sliceKey() == null || update.sliceKey().isBlank()) {
            throw new IllegalArgumentException("IndicatorSliceUpdate.sliceKey 不能为空");
        }
        if (!storage.hasWriteTarget()) {
            throw new IllegalStateException("indicator.storage 未启用任何写入目标（write-redis / write-es）");
        }

        boolean redisOk = false;
        boolean esOk = false;
        RedisUnavailableException redisEx = null;
        EsStore.EsUnavailableException esEx = null;

        if (storage.isWriteRedis()) {
            try {
                redisStore.incrementSlice(update.sliceKey(), update.increment(), update.ttlSeconds());
                redisOk = true;
            } catch (RedisUnavailableException ex) {
                redisEx = ex;
                log.warn("Redis 切片增量失败: {} 原因={}", update.sliceKey(), ex.getMessage());
            }
        }

        if (storage.isWriteEs()) {
            try {
                double current = esStore.readSlice(update.refName(), update.dimensionKey(), update.sliceTs())
                        .orElse(0d);
                esStore.write(update.refName(), update.dimensionKey(), update.sliceTs(),
                        current + update.increment(), update.orderId());
                esOk = true;
            } catch (EsStore.EsUnavailableException ex) {
                esEx = ex;
                log.warn("ES 切片增量失败: {} 原因={}", update.sliceKey(), ex.getMessage());
            }
        }

        if (storage.isWriteRedis() && !redisOk && storage.isWriteEs() && esOk) {
            return;
        }
        if (storage.isWriteEs() && !esOk && storage.isWriteRedis() && redisOk) {
            return;
        }
        if (redisEx != null && (storage.isWriteEs() && !esOk)) {
            throw redisEx;
        }
        if (esEx != null && (storage.isWriteRedis() && !redisOk)) {
            throw esEx;
        }
        if (storage.isWriteRedis() && !redisOk && redisEx != null) {
            throw redisEx;
        }
        if (storage.isWriteEs() && !esOk && esEx != null) {
            throw esEx;
        }
    }

    /**
     * 写入/覆盖切片值。
     *
     * @param sliceKey    {@link SliceKey} 格式的 Redis 键
     * @param value       指标值
     * @param ttlSeconds  Redis TTL（仅 writeRedis 时生效）
     * @param orderId     关联订单号（ES 审计；API 旁路可传来源标识）
     */
    public void writeSlice(String sliceKey, double value, long ttlSeconds, String orderId) {
        if (!storage.hasWriteTarget()) {
            throw new IllegalStateException("indicator.storage 未启用任何写入目标（write-redis / write-es）");
        }

        boolean redisOk = false;
        boolean esOk = false;
        RedisUnavailableException redisEx = null;
        EsStore.EsUnavailableException esEx = null;

        if (storage.isWriteRedis()) {
            try {
                redisStore.writeSlice(sliceKey, value, ttlSeconds);
                redisOk = true;
            } catch (RedisUnavailableException ex) {
                redisEx = ex;
                log.warn("Redis 切片写入失败: {} 原因={}", sliceKey, ex.getMessage());
            }
        }

        if (storage.isWriteEs()) {
            try {
                SliceKey.Parsed parsed = SliceKey.parse(sliceKey)
                        .orElseThrow(() -> new IllegalArgumentException("无法解析切片 Key: " + sliceKey));
                esStore.write(parsed.refName(), parsed.dimensionKey(), parsed.sliceEpochSec(),
                        value, orderId == null ? "" : orderId);
                esOk = true;
            } catch (EsStore.EsUnavailableException ex) {
                esEx = ex;
                log.warn("ES 切片写入失败: {} 原因={}", sliceKey, ex.getMessage());
            } catch (IllegalArgumentException ex) {
                log.warn("ES 切片写入跳过（Key 非法）: {} 原因={}", sliceKey, ex.getMessage());
                if (!storage.isWriteRedis()) {
                    throw ex;
                }
            }
        }

        if (storage.isWriteRedis() && !redisOk && storage.isWriteEs() && esOk) {
            return;
        }
        if (storage.isWriteEs() && !esOk && storage.isWriteRedis() && redisOk) {
            return;
        }
        if (redisEx != null && (storage.isWriteEs() && !esOk)) {
            throw redisEx;
        }
        if (esEx != null && (storage.isWriteRedis() && !redisOk)) {
            throw esEx;
        }
        if (storage.isWriteRedis() && !redisOk && redisEx != null) {
            throw redisEx;
        }
        if (storage.isWriteEs() && !esOk && esEx != null) {
            throw esEx;
        }
    }
}
