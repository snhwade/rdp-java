package com.riskplatform.engine.infrastructure.metrics;

import com.riskplatform.engine.application.DecisionMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Micrometer 的决策指标实现（R15.2），通过 {@code /actuator/prometheus} 暴露。
 *
 * <p>暴露的指标：
 * <ul>
 *   <li>{@code risk_decision_events_total{decision=...}}：事件处理量（按最终决策分标签）；</li>
 *   <li>{@code risk_decision_duration_seconds}：决策耗时计时器，启用分位（P50/P99）发布；</li>
 *   <li>{@code risk_rule_executed_total} / {@code risk_rule_hit_total}：规则执行数/命中数，
 *       规则命中率 = hit/executed，可由 Prometheus 端用 rate() 派生。</li>
 * </ul>
 */
public class MicrometerDecisionMetrics implements DecisionMetrics {

    private final MeterRegistry registry;
    private final Timer decisionTimer;
    private final Counter executedRulesCounter;
    private final Counter hitRulesCounter;
    // 维护实时命中率 gauge 的派生分子/分母（累计值）
    private final AtomicLong executedTotal = new AtomicLong(0);
    private final AtomicLong hitTotal = new AtomicLong(0);

    public MicrometerDecisionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.decisionTimer = Timer.builder("risk.decision.duration")
                .description("风控决策耗时（用于 P50/P99 分位）")
                .publishPercentiles(0.5, 0.99)
                .percentilePrecision(2)
                .register(registry);
        this.executedRulesCounter = Counter.builder("risk.rule.executed")
                .description("规则执行总数")
                .register(registry);
        this.hitRulesCounter = Counter.builder("risk.rule.hit")
                .description("规则命中总数")
                .register(registry);
        // 规则命中率（命中累计 / 执行累计），无执行时为 0
        registry.gauge("risk.rule.hit.rate", this, m -> {
            long executed = m.executedTotal.get();
            return executed == 0 ? 0.0 : (double) m.hitTotal.get() / executed;
        });
    }

    @Override
    public void recordEvent(String decision, long elapsedMs) {
        // 事件处理量按最终决策分标签计数
        registry.counter("risk.decision.events", "decision", decision == null ? "UNKNOWN" : decision)
                .increment();
        decisionTimer.record(Duration.ofMillis(Math.max(0, elapsedMs)));
    }

    @Override
    public void recordRuleExecution(int executedRules, int hitRules) {
        if (executedRules > 0) {
            executedRulesCounter.increment(executedRules);
            executedTotal.addAndGet(executedRules);
        }
        if (hitRules > 0) {
            hitRulesCounter.increment(hitRules);
            hitTotal.addAndGet(hitRules);
        }
    }
}
