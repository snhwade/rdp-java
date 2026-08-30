package com.riskplatform.rating.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商户评分与五档映射单元测试（R12.1/R12.2）。
 */
class RatingScorerTest {

    @Test
    void score_deterministic_sameInputSameScore() {
        RatingScorer scorer = new RatingScorer(Map.of("a", 10d, "b", 5d));
        Map<String, Double> factors = Map.of("a", 3d, "b", 2d); // 30 + 10 = 40
        int first = scorer.score(factors);
        int second = scorer.score(factors);
        assertThat(first).isEqualTo(40);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void score_independentOfFactorIterationOrder() {
        RatingScorer scorer = new RatingScorer(Map.of("a", 2d, "b", 3d, "c", 1d));
        Map<String, Double> m1 = new LinkedHashMap<>();
        m1.put("a", 4d);
        m1.put("b", 5d);
        m1.put("c", 6d);
        Map<String, Double> m2 = new LinkedHashMap<>();
        m2.put("c", 6d);
        m2.put("b", 5d);
        m2.put("a", 4d);
        assertThat(scorer.score(m1)).isEqualTo(scorer.score(m2));
    }

    @Test
    void score_clampedToRange() {
        RatingScorer scorer = new RatingScorer(Map.of("a", 1000d));
        assertThat(scorer.score(Map.of("a", 1d))).isEqualTo(100);
        RatingScorer negative = new RatingScorer(Map.of("a", -1000d));
        assertThat(negative.score(Map.of("a", 1d))).isEqualTo(0);
    }

    @Test
    void riskLevel_boundaries_mapCorrectly() {
        assertThat(RiskLevel.fromScore(0)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(20)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(21)).isEqualTo(RiskLevel.MID_LOW);
        assertThat(RiskLevel.fromScore(40)).isEqualTo(RiskLevel.MID_LOW);
        assertThat(RiskLevel.fromScore(41)).isEqualTo(RiskLevel.MID);
        assertThat(RiskLevel.fromScore(60)).isEqualTo(RiskLevel.MID);
        assertThat(RiskLevel.fromScore(61)).isEqualTo(RiskLevel.MID_HIGH);
        assertThat(RiskLevel.fromScore(80)).isEqualTo(RiskLevel.MID_HIGH);
        assertThat(RiskLevel.fromScore(81)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.fromScore(100)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void riskLevel_fullCoverageNonOverlap_forAllScores() {
        for (int s = 0; s <= 100; s++) {
            RiskLevel level = RiskLevel.fromScore(s);
            assertThat(level).isNotNull();
            assertThat(s).isBetween(level.getMinInclusive(), level.getMaxInclusive());
        }
    }

    @Test
    void riskLevel_outOfRange_rejected() {
        assertThatThrownBy(() -> RiskLevel.fromScore(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskLevel.fromScore(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
