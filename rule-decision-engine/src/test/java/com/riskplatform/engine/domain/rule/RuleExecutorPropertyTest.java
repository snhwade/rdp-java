package com.riskplatform.engine.domain.rule;

import com.riskplatform.engine.domain.decision.Decision;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: risk-decision-platform, Property 3: 规则执行短路与顺序。
 *
 * <p>规则按 (priority desc, ruleId asc) 确定顺序执行（数值越大优先级越高）；一旦命中一条短路规则，
 * 其后所有更低优先级（数值更小）规则不被执行（执行集合是确定前缀）。
 *
 * <p>Validates: Requirements 5.1, 5.6
 */
class RuleExecutorPropertyTest {

    @Property(tries = 200)
    void executionIsOrderedPrefix_andShortCircuitStops(
            @ForAll("ruleSets") @Size(min = 1, max = 10) List<ExecutableRule> rules) {

        // 评估器：全部命中（以最大化触发短路）
        RuleExecutor executor = new RuleExecutor((expr, ctx) -> true, (r, c) -> { });
        RuleExecutionResult result = executor.execute(rules, Map.of());

        // 期望执行顺序
        List<ExecutableRule> ordered = rules.stream()
                .sorted(Comparator.comparingInt(ExecutableRule::priority).reversed()
                        .thenComparingLong(ExecutableRule::ruleId))
                .toList();

        // 计算期望执行的前缀：直到（含）第一个短路规则为止
        int expectedCount = ordered.size();
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).shortCircuited()) {
                expectedCount = i + 1;
                break;
            }
        }

        // 执行记录的 ruleId 序列必须等于 ordered 的前 expectedCount 个
        List<Long> executedIds = result.records().stream().map(RuleExecutionRecord::ruleId).toList();
        List<Long> expectedIds = new ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            expectedIds.add(ordered.get(i).ruleId());
        }
        assertThat(executedIds).isEqualTo(expectedIds);
    }

    @Provide
    Arbitrary<List<ExecutableRule>> ruleSets() {
        Arbitrary<Integer> priorities = Arbitraries.integers().between(1, 50);
        Arbitrary<Boolean> shortCircuits = Arbitraries.of(true, false);
        return Arbitraries.longs().between(1, 100000).set().ofMinSize(1).ofMaxSize(10)
                .flatMap(idSet -> {
                    List<Long> ids = new ArrayList<>(idSet);
                    Arbitrary<List<Integer>> prios = priorities.list().ofSize(ids.size());
                    Arbitrary<List<Boolean>> scs = shortCircuits.list().ofSize(ids.size());
                    return prios.flatMap(ps -> scs.map(sc -> {
                        List<ExecutableRule> rules = new ArrayList<>();
                        for (int i = 0; i < ids.size(); i++) {
                            rules.add(new ExecutableRule(ids.get(i), 1, ps.get(i),
                                    "e", Decision.REVIEW, sc.get(i)));
                        }
                        return rules;
                    }));
                });
    }
}
