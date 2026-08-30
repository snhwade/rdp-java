package com.riskplatform.gateway.infrastructure.standalone;

import com.riskplatform.gateway.domain.IndicatorReadGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class RedisIndicatorReadGateway implements IndicatorReadGateway {

    private static final Logger log = LoggerFactory.getLogger(RedisIndicatorReadGateway.class);

    private final StringRedisTemplate redis;

    public RedisIndicatorReadGateway(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public double read(String refName, String dimensionKey, int windowDays, String granularity) {
        try {
            Granularity gran = Granularity.valueOf(granularity);
            Instant now = Instant.now();
            List<Long> slices = windowSlices(windowDays, gran, now);
            double sum = 0.0;
            boolean any = false;
            for (Long ts : slices) {
                String key = "ind:" + refName + ":" + dimensionKey + ":" + gran.name() + ":" + ts;
                String raw = redis.opsForValue().get(key);
                if (raw != null && !raw.isBlank()) {
                    sum += Double.parseDouble(raw);
                    any = true;
                }
            }
            return any ? sum : 0.0;
        } catch (Exception ex) {
            log.warn("Agent Redis 读指标失败 ref={} dim={}: {}", refName, dimensionKey, ex.getMessage());
            return 0.0;
        }
    }

    private static List<Long> windowSlices(int windowDays, Granularity granularity, Instant now) {
        long step = granularity.stepSeconds;
        long end = truncateToSlice(now, granularity);
        long start = truncateToSlice(now.minusSeconds((long) windowDays * 86400L), granularity);
        List<Long> slices = new ArrayList<>();
        for (long t = start; t <= end; t += step) {
            slices.add(t);
        }
        return slices;
    }

    private static long truncateToSlice(Instant instant, Granularity granularity) {
        long epochSec = instant.truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        return (epochSec / granularity.stepSeconds) * granularity.stepSeconds;
    }

    enum Granularity {
        MINUTE(60L), HOUR(3600L), DAY(86400L);
        final long stepSeconds;

        Granularity(long stepSeconds) {
            this.stepSeconds = stepSeconds;
        }
    }
}
