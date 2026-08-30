package com.riskplatform.indicator.application;

import com.riskplatform.indicator.application.logical.LogicalIndicatorProvider;
import com.riskplatform.indicator.domain.EsStore;
import com.riskplatform.indicator.domain.IndicatorReadResult;
import com.riskplatform.indicator.domain.RedisUnavailableException;
import com.riskplatform.indicator.domain.SliceGranularity;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: risk-decision-platform, Property 6: 指标读取回退与默认值。
 *
 * <p>对任意 (Redis 可用性, ES 可用性) 组合：
 * Redis 命中→返回 Redis 值；Redis 缺失/不可用且 ES 可读→返回 ES 值；
 * 两者均不可读→返回默认值且必标记缺失（记录一次缺失）。
 *
 * <p>Validates: Requirements 9.4, 16.3
 */
class IndicatorReadServicePropertyTest {

    private static final double REDIS_VALUE = 10.0;
    private static final double ES_VALUE = 20.0;
    private static final double DEFAULT_VALUE = -1.0;
    private static final Instant NOW = Instant.parse("2024-01-02T00:00:00Z");

    enum RedisState { HIT, MISS, UNAVAILABLE }

    enum EsState { HIT, MISS, UNAVAILABLE }

    @Property(tries = 200)
    void routingAndDefault(@ForAll RedisState redis, @ForAll EsState es) {
        IndicatorReadService.RedisReader redisReader = (r, d, w, g, n) -> switch (redis) {
            case HIT -> Optional.of(REDIS_VALUE);
            case MISS -> Optional.empty();
            case UNAVAILABLE -> throw new RedisUnavailableException("redis down");
        };
        EsStore esStore = new EsStore() {
            @Override
            public void write(String refName, String dimensionKey, long sliceTs, double value, String orderId) {
            }

            @Override
            public Optional<Double> readSlice(String refName, String dimensionKey, long sliceTs) {
                return Optional.empty();
            }

            @Override
            public Optional<Double> readWindow(String refName, String dimensionKey, int windowDays,
                                               SliceGranularity granularity, Instant now) {
                return switch (es) {
                    case HIT -> Optional.of(ES_VALUE);
                    case MISS -> Optional.empty();
                    case UNAVAILABLE -> throw new EsUnavailableException("es down");
                };
            }
        };

        IndicatorReadService service = new IndicatorReadService(redisReader, esStore, emptyLogicalProvider(), new StorageProperties());
        IndicatorReadResult result = service.read("ind", "m#1", 7, SliceGranularity.HOUR, NOW, () -> DEFAULT_VALUE);

        if (redis == RedisState.HIT) {
            assertThat(result.source()).isEqualTo(IndicatorReadResult.Source.REDIS);
            assertThat(result.value()).isEqualTo(REDIS_VALUE);
            assertThat(result.missing()).isFalse();
        } else if (es == EsState.HIT) {
            assertThat(result.source()).isEqualTo(IndicatorReadResult.Source.ES);
            assertThat(result.value()).isEqualTo(ES_VALUE);
            assertThat(result.missing()).isFalse();
        } else {
            assertThat(result.source()).isEqualTo(IndicatorReadResult.Source.DEFAULT);
            assertThat(result.value()).isEqualTo(DEFAULT_VALUE);
            assertThat(result.missing()).isTrue();
        }
    }

    private static LogicalIndicatorProvider emptyLogicalProvider() {
        return new LogicalIndicatorProvider(null, "http://localhost:8082") {
            @Override
            public Optional<com.riskplatform.indicator.application.logical.LogicalIndicatorDefinition> findOnline(String refName) {
                return Optional.empty();
            }
        };
    }
}
