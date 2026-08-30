package com.riskplatform.engine.domain.decision;

import com.riskplatform.engine.domain.rule.HitDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 决策聚合单元测试（R6.1–R6.4）。
 */
class DecisionAggregatorTest {

    private final DecisionAggregator aggregator = new DecisionAggregator();

    @Test
    void empty_returnsPass() {
        assertThat(aggregator.aggregate(List.of())).isEqualTo(Decision.PASS);
        assertThat(aggregator.aggregate(null)).isEqualTo(Decision.PASS);
    }

    @Test
    void picksMaxPriorityDecision() {
        List<HitDecision> hits = List.of(
                new HitDecision(1L, 100, Decision.REJECT),
                new HitDecision(2L, 10, Decision.PASS));
        // 优先级数值最大=100 的 REJECT 胜出
        assertThat(aggregator.aggregate(hits)).isEqualTo(Decision.REJECT);
    }

    @Test
    void samePriority_picksStrictest() {
        List<HitDecision> hits = List.of(
                new HitDecision(1L, 10, Decision.REVIEW),
                new HitDecision(2L, 10, Decision.REJECT),
                new HitDecision(3L, 10, Decision.PASS));
        assertThat(aggregator.aggregate(hits)).isEqualTo(Decision.REJECT);
    }

    @Test
    void lowerPriorityDecisionDoesNotOverride() {
        List<HitDecision> hits = List.of(
                new HitDecision(1L, 5, Decision.PASS),
                new HitDecision(2L, 9999, Decision.REJECT));
        assertThat(aggregator.aggregate(hits)).isEqualTo(Decision.REJECT);
    }
}
