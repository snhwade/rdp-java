package com.riskplatform.indicator.infrastructure.config;

import com.riskplatform.indicator.application.IndicatorReadService;
import com.riskplatform.indicator.application.IndicatorStorageWriter;
import com.riskplatform.indicator.application.StorageProperties;
import com.riskplatform.indicator.application.logical.LogicalIndicatorCatalog;
import com.riskplatform.indicator.domain.EsStore;
import com.riskplatform.indicator.domain.RedisSliceAggregator;
import com.riskplatform.indicator.domain.SliceStore;
import com.riskplatform.indicator.infrastructure.redis.AggregatingRedisReader;
import com.riskplatform.indicator.infrastructure.redis.RedisSliceStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 指标存储服务领域/应用组件装配（R9.3/R9.4）。
 */
@Configuration
@Import(com.riskplatform.common.web.GlobalExceptionHandler.class)
@EnableConfigurationProperties(StorageProperties.class)
public class AppServiceConfig {

    @Bean
    public SliceStore sliceStore(StringRedisTemplate redisTemplate) {
        return new RedisSliceStore(redisTemplate);
    }

    @Bean
    public IndicatorStorageWriter indicatorStorageWriter(StorageProperties storage,
                                                         SliceStore sliceStore,
                                                         EsStore esStore) {
        return new IndicatorStorageWriter(storage, sliceStore, esStore);
    }

    @Bean
    public RedisSliceAggregator redisSliceAggregator(SliceStore sliceStore) {
        return new RedisSliceAggregator(sliceStore);
    }

    @Bean
    public IndicatorReadService.RedisReader redisReader(SliceStore sliceStore) {
        return new AggregatingRedisReader(sliceStore);
    }

    @Bean
    public IndicatorReadService indicatorReadService(IndicatorReadService.RedisReader redisReader,
                                                     EsStore esStore,
                                                     LogicalIndicatorCatalog logicalProvider,
                                                     StorageProperties storage) {
        return new IndicatorReadService(redisReader, esStore, logicalProvider, storage);
    }
}
