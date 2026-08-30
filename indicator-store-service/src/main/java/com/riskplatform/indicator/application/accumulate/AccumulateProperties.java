package com.riskplatform.indicator.application.accumulate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 指标累计配置（绑定 {@code indicator.accumulate.*}）。
 *
 * <p>指标定义真源在 rule-config；此处配置<strong>累计链路</strong>与 Kafka 消费参数。
 * 通过 {@link #mode} 在 Flink（推荐）与 service 自消费之间切换，避免双写。
 */
@ConfigurationProperties(prefix = "indicator.accumulate")
public class AccumulateProperties {

    /** 是否启用指标累计（默认启用）。 */
    private boolean enabled = true;
    /**
     * 累计链路模式，环境变量 {@code INDICATOR_ACCUMULATE_MODE}：
     * <ul>
     *   <li>{@code flink}（默认）：Kafka → Flink 计算 → Kafka 切片增量 → 本服务消费落库；</li>
     *   <li>{@code service}：Kafka → 本服务 {@code @KafkaListener}（轻量/降级）。</li>
     * </ul>
     */
    private String mode = AccumulateMode.FLINK.name().toLowerCase();
    /** 订单终态主题（service 模式消费）。 */
    private String topic = "order-final-state";
    /** 订单终态消费组（service 模式）。 */
    private String group = "indicator-accumulation";
    /** Flink 回写的指标切片增量主题（flink 模式消费）。 */
    private String sliceTopic = "indicator-slice-updates";
    /** 切片增量消费组（flink 模式；可部署多实例做同构写入，或不同 group 写不同存储）。 */
    private String sliceGroup = "indicator-slice-writer";
    /** 死信主题。 */
    private String dlqTopic = "order-final-state-dlq";
    /** 指标定义刷新周期（毫秒）。 */
    private long refreshMs = 30_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = AccumulateMode.from(mode).name().toLowerCase();
    }

    public AccumulateMode resolvedMode() {
        return AccumulateMode.from(mode);
    }

    /** 本服务是否应运行订单终态 @KafkaListener：enabled 且 mode=service。 */
    public boolean isServiceConsumerActive() {
        return enabled && resolvedMode() == AccumulateMode.SERVICE;
    }

    /** 是否由 Flink 承担累计（本服务消费切片增量主题）。 */
    public boolean isFlinkMode() {
        return enabled && resolvedMode() == AccumulateMode.FLINK;
    }

    /** flink 模式下是否应运行切片增量消费者。 */
    public boolean isSliceConsumerActive() {
        return isFlinkMode();
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getSliceTopic() {
        return sliceTopic;
    }

    public void setSliceTopic(String sliceTopic) {
        this.sliceTopic = sliceTopic;
    }

    public String getSliceGroup() {
        return sliceGroup;
    }

    public void setSliceGroup(String sliceGroup) {
        this.sliceGroup = sliceGroup;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public long getRefreshMs() {
        return refreshMs;
    }

    public void setRefreshMs(long refreshMs) {
        this.refreshMs = refreshMs;
    }
}
