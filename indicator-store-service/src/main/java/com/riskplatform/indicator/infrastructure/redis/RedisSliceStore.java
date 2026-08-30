package com.riskplatform.indicator.infrastructure.redis;

import com.riskplatform.indicator.domain.RedisUnavailableException;
import com.riskplatform.indicator.domain.SliceStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redis 切片存储实现（R9.1/R9.2/R9.3）。
 *
 * <p>基于 {@link StringRedisTemplate} 读写切片值，切片 Key 由领域层 {@code SliceKey} 生成。
 * 读取走 {@code MGET} 批量获取，命中即在毫秒级返回（R9.3 目标 ≤50ms）。
 *
 * <p>Redis 访问异常统一包装为 {@link RedisUnavailableException}，由读路由
 * {@code IndicatorReadService} 据此回退至 ES（R9.4）。
 */
public class RedisSliceStore implements SliceStore {

    private final StringRedisTemplate redisTemplate;

    public RedisSliceStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void writeSlice(String key, double value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, Double.toString(value), Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException("Redis 写入失败: " + ex.getMessage());
        }
    }

    @Override
    public void incrementSlice(String key, double increment, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().increment(key, increment);
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException("Redis 增量写入失败: " + ex.getMessage());
        }
    }

    @Override
    public Optional<Double> readSlice(String key) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            return parse(raw);
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException("Redis 读取失败: " + ex.getMessage());
        }
    }

    @Override
    public List<Double> readSlices(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        try {
            List<String> raws = redisTemplate.opsForValue().multiGet(keys);
            List<Double> values = new ArrayList<>();
            if (raws != null) {
                for (String raw : raws) {
                    parse(raw).ifPresent(values::add);
                }
            }
            return values;
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException("Redis 批量读取失败: " + ex.getMessage());
        }
    }

    @Override
    public boolean markProcessedIfAbsent(String dedupKey, long ttlSeconds) {
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", Duration.ofSeconds(ttlSeconds));
            return Boolean.TRUE.equals(ok);
        } catch (DataAccessException ex) {
            throw new RedisUnavailableException("Redis 幂等标记失败: " + ex.getMessage());
        }
    }

    /** 解析切片字符串值，空值/非法数字视为缺失。 */
    private Optional<Double> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(raw));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
