package com.riskplatform.indicator.adapter.accumulate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.model.IndicatorSliceUpdate;
import com.riskplatform.indicator.application.accumulate.IndicatorSliceUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Flink 模式：消费 {@code indicator-slice-updates}，写入 Redis / ES 等存储。
 */
public class IndicatorSliceUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(IndicatorSliceUpdateConsumer.class);

    private final IndicatorSliceUpdateService sliceUpdateService;
    private final ObjectMapper objectMapper;

    public IndicatorSliceUpdateConsumer(IndicatorSliceUpdateService sliceUpdateService,
                                        ObjectMapper objectMapper) {
        this.sliceUpdateService = sliceUpdateService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${indicator.accumulate.slice-topic}",
            groupId = "${indicator.accumulate.slice-group}")
    public void onMessage(String payload) {
        try {
            IndicatorSliceUpdate update = objectMapper.readValue(payload, IndicatorSliceUpdate.class);
            sliceUpdateService.apply(update);
        } catch (Exception e) {
            log.warn("指标切片增量消息解析/处理失败，已丢弃: {} 原因={}", payload, e.getMessage());
        }
    }
}
