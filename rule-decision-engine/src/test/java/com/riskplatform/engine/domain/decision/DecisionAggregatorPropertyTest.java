package com.riskplatform.engine.domain.decision;

import com.riskplatform.engine.domain.rule.HitDecision;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: risk-decision-platform, Property 1: 决策聚合 — 最高优先级最严格者。
 *
 * <p>对任意命中决策集合 H：
 * 空 → PASS；非空 → H 中"优先级数值最大"子集里"严格性最高"的决策。
 * 元属性：结果与输入顺序无关（置换不变）；增加优先级更低的决策不改变结果（单调）。
 *
 * <p>Validates: Requirements 6.2, 6.3, 6.4
 */
class DecisionAggregatorPropertyTest {

    private final DecisionAggregator aggregator = new DecisionAggregator();

    @Property(tries = 300)
    void resultEqualsMaxPriorityStrictest(@ForAll("hits") @Size(min = 1, max = 15) List<HitDecision> hits) {
        Decision actual = aggregator.aggregate(hits);

        int maxPriority = hits.stream().mapToInt(HitDecision::priority).max().orElseThrow();
        Decision expected = hits.stream()
                .filter(h -> h.priority() == maxPriority)
                .map(HitDecision::decision)
                .max(java.util.Comparator.comparingInt(Decision::strictness))
                .orElseThrow();
        assertThat(actual).isEqualTo(expected);
    }

    @Property(tries = 300)
    void permutationInvariant(@ForAll("hits") @Size(min = 1, max = 15) List<HitDecision> hits) {
        Decision first = aggregator.aggregate(hits);
        List<HitDecision> shuffled = new ArrayList<>(hits);
        Collections.shuffle(shuffled);
        assertThat(aggregator.aggregate(shuffled)).isEqualTo(first);
    }

    @Property(tries = 300)
    void emptyAlwaysPass(@ForAll("emptyOnly") List<HitDecision> hits) {
        assertThat(aggregator.aggregate(hits)).isEqualTo(Decision.PASS);
    }

    @Provide
    Arbitrary<List<HitDecision>> hits() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1, 100000);
        Arbitrary<Integer> priorities = Arbitraries.integers().between(1, 9999);
        Arbitrary<Decision> decisions = Arbitraries.of(Decision.class);
        Arbitrary<HitDecision> one = Combinators.combine(ids, priorities, decisions).as(HitDecision::new);
        return one.list().ofMinSize(1).ofMaxSize(15);
    }

    @Provide
    Arbitrary<List<HitDecision>> emptyOnly() {
        return Arbitraries.just(List.of());
    }
}
