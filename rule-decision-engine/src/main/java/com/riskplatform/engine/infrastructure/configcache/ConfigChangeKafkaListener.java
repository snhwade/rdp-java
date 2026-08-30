package com.riskplatform.engine.infrastructure.configcache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 订阅 rule-config 配置变更主题，失效本地缓存（enhancement-plan T6）。
 * Kafka key=configType，value=configId。
 */
@Component
@ConditionalOnProperty(name = "rdp.config-cache.kafka-enabled", havingValue = "true")
public class ConfigChangeKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeKafkaListener.class);

    private final ConfigCacheRegistry registry;

    public ConfigChangeKafkaListener(ConfigCacheRegistry registry) {
        this.registry = registry;
    }

    @KafkaListener(
            topics = "${config.change-topic:rule-config-change}",
            groupId = "${spring.application.name:rule-decision-engine}-config-cache")
    public void onChange(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        String type = record.key();
        String id = record.value();
        log.info("收到配置变更: type={} id={}", type, id);
        if (type == null) {
            registry.invalidateAll();
            return;
        }
        String normalized = type.trim().toUpperCase();
        // 规则变更会影响多个规则包，整桶失效
        if ("RULE_V2".equals(normalized) || "RULE".equals(normalized)) {
            registry.invalidate("RULE_PACKAGE", "*");
            return;
        }
        registry.invalidate(normalized, id);
    }
}
