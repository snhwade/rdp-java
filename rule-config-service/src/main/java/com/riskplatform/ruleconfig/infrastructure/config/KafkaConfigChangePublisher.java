package com.riskplatform.ruleconfig.infrastructure.config;

import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Kafka 的配置变更发布实现（R3.4/R3.9）。
 *
 * <p>向配置变更主题发布失效消息，引擎服务订阅后刷新本地缓存。
 */
@Component
public class KafkaConfigChangePublisher implements ConfigChangePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${config.change-topic:rule-config-change}")
    private String topic;

    public KafkaConfigChangePublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishChange(String configType, String configId) {
        // 配置变更广播为「尽力而为」：Kafka 不可用时不应阻断配置 CRUD。
        // 引擎/消费侧另有定时刷新兜底，故此处捕获异常仅记录告警。
        try {
            kafkaTemplate.send(topic, configType, configId);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(KafkaConfigChangePublisher.class)
                    .warn("配置变更广播失败（Kafka 不可用），已忽略：{}={} 原因={}",
                            configType, configId, e.getMessage());
        }
    }
}
