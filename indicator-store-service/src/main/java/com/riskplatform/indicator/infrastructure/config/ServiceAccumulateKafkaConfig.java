package com.riskplatform.indicator.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.indicator.adapter.accumulate.OrderFinalStateConsumer;
import com.riskplatform.indicator.application.accumulate.AccumulateMode;
import com.riskplatform.indicator.application.accumulate.AccumulateProperties;
import com.riskplatform.indicator.application.accumulate.IndicatorAccumulateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 方案 A：indicator-store 自消费 Kafka 累计（仅 {@code mode=service} 时装配）。
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "indicator.accumulate", name = "mode", havingValue = "service")
public class ServiceAccumulateKafkaConfig {

    @Bean
    public OrderFinalStateConsumer orderFinalStateConsumer(
            IndicatorAccumulateService service, ObjectMapper objectMapper) {
        return new OrderFinalStateConsumer(service, objectMapper);
    }
}
