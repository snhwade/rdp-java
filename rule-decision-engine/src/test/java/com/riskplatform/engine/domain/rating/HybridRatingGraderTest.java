package com.riskplatform.engine.domain.rating;

import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRatingGraderTest {

    private static final RuleExpressionEvaluator FLAG_EVAL = (condition, context) -> {
        Object flag = context.get(condition);
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    };

    private final HybridRatingGrader grader = new HybridRatingGrader(
            new ScoreBasedGrader(FLAG_EVAL),
            new DirectGrader(FLAG_EVAL));

    private static List<GradeBand> bands() {
        return List.of(
                new GradeBand(new BigDecimal("0"), new BigDecimal("40"), "一级"),
                new GradeBand(new BigDecimal("40"), new BigDecimal("70"), "二级"),
                new GradeBand(new BigDecimal("70"), new BigDecimal("100"), "三级"));
    }

    @Test
    void mixedGrading_combinesScoreAndDirectHits() {
        List<RatingItem> scoreItems = List.of(
                RatingItem.of("scoreHit", new BigDecimal("30"), null));
        List<DirectGradingItem> directItems = List.of(
                DirectGradingItem.of("directHit", "三级"));

        HybridRatingGrader.HybridRatingResult result = grader.grade(
                scoreItems,
                directItems,
                bands(),
                Map.of("scoreHit", true, "directHit", true));

        assertThat(result.totalScore()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(result.grade()).isEqualTo("三级");
        assertThat(result.directScoreContribution()).isEqualByComparingTo(new BigDecimal("70"));
    }

    @Test
    void mixedGrading_scoreOnly_whenDirectNotHit() {
        HybridRatingGrader.HybridRatingResult result = grader.grade(
                List.of(RatingItem.of("scoreHit", new BigDecimal("45"), null)),
                List.of(DirectGradingItem.of("directHit", "三级")),
                bands(),
                Map.of("scoreHit", true, "directHit", false));

        assertThat(result.totalScore()).isEqualByComparingTo(new BigDecimal("45"));
        assertThat(result.grade()).isEqualTo("二级");
    }
}
