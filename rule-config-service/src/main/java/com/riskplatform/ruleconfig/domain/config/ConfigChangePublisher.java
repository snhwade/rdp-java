package com.riskplatform.ruleconfig.domain.config;

/**
 * 配置变更发布端口（R3.4/R3.9）。
 *
 * <p>配置（规则/规则组/选择器/指标定义）发生变更时，通过 Kafka 配置变更主题广播失效消息，
 * 引擎服务订阅后刷新本地缓存，使规则启停在 5 秒内生效。由基础设施层用 Spring Kafka 实现。
 */
public interface ConfigChangePublisher {

    /**
     * 发布一条配置变更事件。
     *
     * @param configType 配置类型（RULE/RULE_PACKAGE/INDICATOR 等）
     * @param configId   配置标识
     */
    void publishChange(String configType, String configId);
}
