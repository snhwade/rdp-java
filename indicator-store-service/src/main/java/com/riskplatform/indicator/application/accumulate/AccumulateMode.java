package com.riskplatform.indicator.application.accumulate;

/**
 * 指标累计链路模式（S17 双方案切换）。
 *
 * <ul>
 *   <li>{@link #FLINK}（推荐/默认）：Kafka → Flink 计算 → Kafka 切片增量 → 存储消费者；</li>
 *   <li>{@link #SERVICE}：Kafka → 本服务 {@code @KafkaListener} 轻量累计（开发/降级）。</li>
 * </ul>
 */
public enum AccumulateMode {

    FLINK,
    SERVICE;

    public static AccumulateMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return FLINK;
        }
        String normalized = raw.trim().toLowerCase();
        for (AccumulateMode m : values()) {
            if (m.name().equalsIgnoreCase(normalized)) {
                return m;
            }
        }
        throw new IllegalArgumentException(
                "indicator.accumulate.mode 非法: " + raw + "，仅支持 flink | service");
    }
}
