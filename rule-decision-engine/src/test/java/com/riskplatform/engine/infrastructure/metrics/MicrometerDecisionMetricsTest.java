package com.riskplatform.engine.infrastructure.metrics;

import com.riskplatform.engine.application.DecisionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Micrometer 决策指标埋点单元测试（R15.2）。
 *
 * <p>使用 {@link SimpleMeterRegistry}（内存注册表，无需 Prometheus 端点）验证：
 * 事件量计数、决策耗时计时、规则执行/命中计数与命中率派生。
 */
class MicrometerDecisionMetricsTest {

    @Test
    void recordEvent_incrementsCounterAndTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DecisionMetrics metrics = new MicrometerDecisionMetrics(registry);

        metrics.recordEvent("REJECT", 30);
        metrics.recordEvent("REJECT", 50);
        metrics.recordEvent("PASS", 10);

        assertThat(registry.get("risk.decision.events").tag("decision", "REJECT").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("risk.decision.events").tag("decision", "PASS").counter().count())
                .isEqualTo(1.0);
        // 计时器记录了 3 次决策耗时
        assertThat(registry.get("risk.decision.duration").timer().count()).isEqualTo(3L);
    }

    @Test
    void recordRuleExecution_tracksHitRate() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DecisionMetrics metrics = new MicrometerDecisionMetrics(registry);

        metrics.recordRuleExecution(4, 1);
        metrics.recordRuleExecution(6, 3);

        assertThat(registry.get("risk.rule.executed").counter().count()).isEqualTo(10.0);
        assertThat(registry.get("risk.rule.hit").counter().count()).isEqualTo(4.0);
        // 命中率 = 4 / 10 = 0.4
        assertThat(registry.get("risk.rule.hit.rate").gauge().value()).isEqualTo(0.4);
    }

    @Test
    void hitRate_zeroWhenNoExecution() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new MicrometerDecisionMetrics(registry);
        assertThat(registry.get("risk.rule.hit.rate").gauge().value()).isEqualTo(0.0);
    }
}
