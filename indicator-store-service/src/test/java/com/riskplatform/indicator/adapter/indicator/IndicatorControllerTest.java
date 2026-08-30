package com.riskplatform.indicator.adapter.indicator;

import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.indicator.application.IndicatorReadService;
import com.riskplatform.indicator.application.IndicatorStorageWriter;
import com.riskplatform.indicator.application.StorageProperties;
import com.riskplatform.indicator.application.logical.LogicalIndicatorProvider;
import com.riskplatform.indicator.domain.EsStore;
import com.riskplatform.indicator.domain.RedisUnavailableException;
import com.riskplatform.indicator.domain.SliceGranularity;
import com.riskplatform.indicator.infrastructure.stats.IndicatorRuntimeStatsWriter;
import com.riskplatform.indicator.infrastructure.standalone.IndicatorRuntimeStatsWriteMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 指标存储 REST 适配器 Web 层测试（R9.3/R9.4）。
 *
 * <p>使用 MockMvc standaloneSetup（无需 Redis/ES/数据库），覆盖：
 * <ul>
 *   <li>Redis 命中返回值（source=REDIS、missing=false）；</li>
 *   <li>两源均不可读时按默认值策略返回（source=DEFAULT、missing=true）；</li>
 *   <li>缺失维度键/窗口/粒度、引用名格式非法、粒度非法等参数校验返回 400 结构化错误。</li>
 * </ul>
 */
class IndicatorControllerTest {

    /** 构造一个 Redis 命中固定值、ES 永不命中的读路由服务。 */
    private MockMvc mockMvcWithRedisHit(double redisValue) {
        IndicatorReadService.RedisReader redisReader =
                (r, d, w, g, n) -> Optional.of(redisValue);
        EsStore esStore = emptyEsStore();
        return build(new IndicatorReadService(redisReader, esStore, emptyLogicalProvider(), new StorageProperties()));
    }

    private LogicalIndicatorProvider emptyLogicalProvider() {
        return new LogicalIndicatorProvider(null, "http://localhost:8082") {
            @Override
            public java.util.Optional<com.riskplatform.indicator.application.logical.LogicalIndicatorDefinition> findOnline(String refName) {
                return java.util.Optional.empty();
            }
        };
    }

    /** 构造一个 Redis 不可用、ES 也不可读的读路由服务（触发默认值与缺失标记）。 */
    private MockMvc mockMvcWithBothUnavailable() {
        IndicatorReadService.RedisReader redisReader = (r, d, w, g, n) -> {
            throw new RedisUnavailableException("redis down");
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
                throw new EsUnavailableException("es down");
            }
        };
        return build(new IndicatorReadService(redisReader, esStore, emptyLogicalProvider(), new StorageProperties()));
    }

    private EsStore emptyEsStore() {
        return new EsStore() {
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
                return Optional.empty();
            }
        };
    }

    private MockMvc build(IndicatorReadService service) {
        StorageProperties storage = new StorageProperties();
        storage.setWriteRedis(false);
        storage.setWriteEs(false);
        IndicatorStorageWriter writer = new IndicatorStorageWriter(storage,
                new com.riskplatform.indicator.domain.SliceStore() {
                    @Override
                    public void writeSlice(String key, double value, long ttlSeconds) {
                    }

                    @Override
                    public void incrementSlice(String key, double increment, long ttlSeconds) {
                    }

                    @Override
                    public Optional<Double> readSlice(String key) {
                        return Optional.empty();
                    }

                    @Override
                    public java.util.List<Double> readSlices(java.util.List<String> keys) {
                        return java.util.List.of();
                    }

                    @Override
                    public boolean markProcessedIfAbsent(String dedupKey, long ttlSeconds) {
                        return true;
                    }
                },
                emptyEsStore());
        IndicatorRuntimeStatsWriter statsWriter = new IndicatorRuntimeStatsWriter(new IndicatorRuntimeStatsWriteMapper() {
            @Override
            public void upsertAccumulate(String refName) {
            }

            @Override
            public void upsertReadMiss(String refName) {
            }
        });
        return MockMvcBuilders.standaloneSetup(new IndicatorController(service, writer, statsWriter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void redisHitReturnsValue() throws Exception {
        mockMvcWithRedisHit(42.0)
                .perform(get("/api/v1/indicators/{refName}", "txn_cnt_1d")
                        .param("dimensionKey", "merchant#M001")
                        .param("windowDays", "1")
                        .param("granularity", "DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refName").value("txn_cnt_1d"))
                .andExpect(jsonPath("$.dimensionKey").value("merchant#M001"))
                .andExpect(jsonPath("$.value").value(42.0))
                .andExpect(jsonPath("$.source").value("REDIS"))
                .andExpect(jsonPath("$.missing").value(false));
    }

    @Test
    void bothUnavailableReturnsDefaultValueAndMissing() throws Exception {
        mockMvcWithBothUnavailable()
                .perform(get("/api/v1/indicators/{refName}", "txn_cnt_1d")
                        .param("dimensionKey", "merchant#M001")
                        .param("windowDays", "7")
                        .param("granularity", "HOUR")
                        .param("defaultValue", "-1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(-1.0))
                .andExpect(jsonPath("$.source").value("DEFAULT"))
                .andExpect(jsonPath("$.missing").value(true));
    }

    @Test
    void missingDimensionKeyReturns400() throws Exception {
        mockMvcWithRedisHit(1.0)
                .perform(get("/api/v1/indicators/{refName}", "txn_cnt_1d")
                        .param("windowDays", "1")
                        .param("granularity", "DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.dimensionKey").exists());
    }

    @Test
    void missingWindowDaysReturns400() throws Exception {
        mockMvcWithRedisHit(1.0)
                .perform(get("/api/v1/indicators/{refName}", "txn_cnt_1d")
                        .param("dimensionKey", "merchant#M001")
                        .param("granularity", "DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.windowDays").exists());
    }

    @Test
    void windowDaysOutOfRangeReturns400() throws Exception {
        mockMvcWithRedisHit(1.0)
                .perform(get("/api/v1/indicators/{refName}", "txn_cnt_1d")
                        .param("dimensionKey", "merchant#M001")
                        .param("windowDays", "0")
                        .param("granularity", "DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.windowDays").exists());
    }

    @Test
    void invalidRefNameReturns400() throws Exception {
        mockMvcWithRedisHit(1.0)
                .perform(get("/api/v1/indicators/{refName}", "bad name!")
                        .param("dimensionKey", "merchant#M001")
                        .param("windowDays", "1")
                        .param("granularity", "DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.refName").exists());
    }

    @Test
    void invalidGranularityReturns400() throws Exception {
        mockMvcWithRedisHit(1.0)
                .perform(get("/api/v1/indicators/{refName}", "txn_cnt_1d")
                        .param("dimensionKey", "merchant#M001")
                        .param("windowDays", "1")
                        .param("granularity", "WEEK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.granularity").exists());
    }
}
