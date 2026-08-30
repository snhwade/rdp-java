package com.riskplatform.engine.infrastructure.standalone;

import com.riskplatform.engine.domain.indicator.IndicatorReader;
import com.riskplatform.engine.infrastructure.standalone.StandaloneSliceKey.StandaloneSliceGranularity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** 从 Redis 读取指标切片聚合值（standalone，不依赖 indicator-store HTTP）。 */
@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class RedisIndicatorReader implements IndicatorReader {

    private static final Logger log = LoggerFactory.getLogger(RedisIndicatorReader.class);

    private final StringRedisTemplate redis;

    public RedisIndicatorReader(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public double read(String refName, String dimensionKey, int windowDays, String granularity) {
        try {
            StandaloneSliceGranularity gran = StandaloneSliceGranularity.valueOf(granularity);
            Instant now = Instant.now();
            List<Long> slices = StandaloneSliceKey.windowSlices(windowDays, gran, now);
            double sum = 0.0;
            boolean any = false;
            for (Long ts : slices) {
                String key = StandaloneSliceKey.of(refName, dimensionKey, gran, ts);
                String raw = redis.opsForValue().get(key);
                if (raw != null && !raw.isBlank()) {
                    sum += Double.parseDouble(raw);
                    any = true;
                }
            }
            return any ? sum : 0.0;
        } catch (Exception ex) {
            log.warn("Redis 读取指标失败，按 0 处理: ref={} dim={} 原因={}", refName, dimensionKey, ex.getMessage());
            return 0.0;
        }
    }
}
