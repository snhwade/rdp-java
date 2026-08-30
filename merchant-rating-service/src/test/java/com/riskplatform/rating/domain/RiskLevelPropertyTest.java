package com.riskplatform.rating.domain;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: risk-decision-platform, Property 7: 商户评级映射全覆盖不重叠。
 *
 * <p>对任意评分 s∈[0,100]：恰好映射五档之一，区间互不重叠且完全覆盖；
 * 相同输入产出相同评分（确定性）。
 *
 * <p>Validates: Requirements 12.1, 12.2
 */
class RiskLevelPropertyTest {

    @Property(tries = 300)
    void everyScoreMapsToExactlyOneLevel(@ForAll @IntRange(min = 0, max = 100) int score) {
        RiskLevel level = RiskLevel.fromScore(score);
        assertThat(level).isNotNull();
        // 命中区间
        assertThat(score).isBetween(level.getMinInclusive(), level.getMaxInclusive());
        // 不被任何其它档命中（互不重叠）
        long matchCount = java.util.Arrays.stream(RiskLevel.values())
                .filter(l -> score >= l.getMinInclusive() && score <= l.getMaxInclusive())
                .count();
        assertThat(matchCount).isEqualTo(1);
    }

    @Property(tries = 200)
    void scoringIsDeterministic(@ForAll @IntRange(min = 0, max = 10) int va,
                                @ForAll @IntRange(min = 0, max = 10) int vb) {
        RatingScorer scorer = new RatingScorer(Map.of("a", 3.0, "b", 2.0));
        Map<String, Double> factors = Map.of("a", (double) va, "b", (double) vb);
        int first = scorer.score(factors);
        int second = scorer.score(factors);
        assertThat(second).isEqualTo(first);
        assertThat(first).isBetween(0, 100);
    }

    @Property(tries = 100)
    void levelBandsAreContiguousAndCover0To100(@ForAll @IntRange(min = 0, max = 99) int score) {
        // 相邻分数若跨档，则前一档的 max+1 必等于后一档的 min（无缝衔接）
        RiskLevel current = RiskLevel.fromScore(score);
        RiskLevel next = RiskLevel.fromScore(score + 1);
        if (current != next) {
            assertThat(current.getMaxInclusive() + 1).isEqualTo(next.getMinInclusive());
        }
    }
}
