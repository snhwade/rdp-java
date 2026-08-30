package com.riskplatform.engine.domain.decision;

import com.riskplatform.engine.domain.rule.HitDecision;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: risk-decision-platform, Property 8: 决策时限边界。
 *
 * <p>对任意配置时限 t∈[1,5000]：t 内完成返回正常决策；超过 t 返回超时处置策略决策并记录原因。
 *
 * <p>Validates: Requirements 6.5
 */
class DecisionServicePropertyTest {

    private final DecisionService service = new DecisionService(new DecisionAggregator());

    @Property(tries = 40)
    void withinDeadline_returnsNormal(@ForAll @IntRange(min = 200, max = 1000) int timeoutMs) {
        DecisionConfig config = new DecisionConfig(timeoutMs, Decision.REVIEW);
        // 立即返回的命中（远快于时限）
        FinalDecision fd = service.decideWithDeadline(
                () -> List.of(new HitDecision(1L, 10, Decision.REJECT)), config);
        assertThat(fd.timedOut()).isFalse();
        assertThat(fd.decision()).isEqualTo(Decision.REJECT);
    }

    @Property(tries = 15)
    void exceedingDeadline_returnsTimeoutDisposition(@ForAll @IntRange(min = 1, max = 50) int timeoutMs) {
        DecisionConfig config = new DecisionConfig(timeoutMs, Decision.REVIEW);
        // 模拟耗时远超时限的产出过程
        FinalDecision fd = service.decideWithDeadline(() -> {
            Thread.sleep(timeoutMs + 500L);
            return List.of(new HitDecision(1L, 10, Decision.PASS));
        }, config);
        assertThat(fd.timedOut()).isTrue();
        assertThat(fd.decision()).isEqualTo(Decision.REVIEW);
        assertThat(fd.timeoutReason()).isNotBlank();
    }
}
