package com.riskplatform.indicator.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.indicator.adapter.accumulate.IndicatorSliceUpdateConsumer;
import com.riskplatform.indicator.application.IndicatorStorageWriter;
import com.riskplatform.indicator.application.accumulate.IndicatorSliceUpdateService;
import com.riskplatform.indicator.infrastructure.stats.IndicatorRuntimeStatsWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 方案 B（默认）：Flink 回写 Kafka 切片增量，本服务消费并落库。
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "indicator.accumulate", name = "mode", havingValue = "flink", matchIfMissing = true)
public class FlinkAccumulateKafkaConfig {

    @Bean
    public IndicatorSliceUpdateService indicatorSliceUpdateService(
            IndicatorStorageWriter storageWriter,
            IndicatorRuntimeStatsWriter runtimeStatsWriter) {
        return new IndicatorSliceUpdateService(storageWriter, runtimeStatsWriter);
    }

    @Bean
    public IndicatorSliceUpdateConsumer indicatorSliceUpdateConsumer(
            IndicatorSliceUpdateService service, ObjectMapper objectMapper) {
        return new IndicatorSliceUpdateConsumer(service, objectMapper);
    }
}
