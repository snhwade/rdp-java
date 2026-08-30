package com.riskplatform.indicator.adapter.accumulate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.indicator.application.accumulate.IndicatorAccumulateService;
import com.riskplatform.indicator.application.accumulate.OrderFinalState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * 订单终态数据 Kafka 消费者（新方案，R8）。
 *
 * <p>消费 {@code order-final-state} 主题，反序列化为 {@link OrderFinalState} 后交由
 * {@link IndicatorAccumulateService} 累计。反序列化失败或缺 orderId 的消息记录后丢弃
 * （生产可路由死信主题），不抛出以保证消费不中断（R8.4）。
 */
public class OrderFinalStateConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderFinalStateConsumer.class);

    private final IndicatorAccumulateService accumulateService;
    private final ObjectMapper objectMapper;

    public OrderFinalStateConsumer(IndicatorAccumulateService accumulateService, ObjectMapper objectMapper) {
        this.accumulateService = accumulateService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${indicator.accumulate.topic:order-final-state}",
            groupId = "${indicator.accumulate.group:indicator-accumulation}")
    public void onMessage(String json) {
        if (json == null || json.isBlank()) {
            log.warn("收到空消息，丢弃");
            return;
        }
        OrderFinalState order;
        try {
            order = objectMapper.readValue(json, OrderFinalState.class);
        } catch (Exception e) {
            // R8.4：反序列化失败丢弃并记录（生产路由死信主题）
            log.warn("订单终态反序列化失败，丢弃: {} 原因={}", json, e.getMessage());
            return;
        }
        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            log.warn("订单终态缺少 orderId，丢弃: {}", json);
            return;
        }
        int applied = accumulateService.accumulate(order);
        log.debug("订单 {} 累计了 {} 个指标", order.getOrderId(), applied);
    }
}
