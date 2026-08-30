package com.riskplatform.indicator.adapter.accumulate;

import com.riskplatform.indicator.application.accumulate.AccumulateMode;
import com.riskplatform.indicator.application.accumulate.AccumulateProperties;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指标累计运行时配置查询（运维/联调：确认当前走哪条累计链路）。
 */
@RestController
@RequestMapping("/api/v1/accumulate")
public class AccumulateRuntimeController {

    private final AccumulateProperties properties;

    public AccumulateRuntimeController(AccumulateProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        AccumulateMode mode = properties.resolvedMode();
        return Map.of(
                "enabled", properties.isEnabled(),
                "mode", mode.name().toLowerCase(),
                "serviceConsumerActive", properties.isServiceConsumerActive(),
                "sliceConsumerActive", properties.isSliceConsumerActive(),
                "orderFinalStateTopic", properties.getTopic(),
                "serviceConsumerGroup", properties.getGroup(),
                "sliceUpdateTopic", properties.getSliceTopic(),
                "sliceConsumerGroup", properties.getSliceGroup(),
                "pipelineDescription", mode == AccumulateMode.FLINK
                        ? "Kafka order-final-state → Flink → Kafka indicator-slice-updates"
                        + " → indicator-store @KafkaListener → Redis/ES → GET /indicators"
                        : "Kafka order-final-state → indicator-store @KafkaListener → Redis/ES → GET /indicators");
    }
}
