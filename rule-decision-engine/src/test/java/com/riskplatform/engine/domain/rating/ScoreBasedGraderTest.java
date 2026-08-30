package com.riskplatform.engine.domain.rating;

import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评分定级器单元测试（R12.2–R12.7）。
 *
 * <p>条件求值器以"上下文中布尔标志"模拟 Aviator：表达式即上下文键名，值为 true 即命中，
 * 便于在不依赖具体表达式引擎下验证计分、封顶、区间映射、越界与未命中标注。
 */
class ScoreBasedGraderTest {

    /** 简化条件求值：表达式作为上下文键，值为 Boolean.TRUE 即命中。 */
    private static final RuleExpressionEvaluator FLAG_EVAL =
            (expr, ctx) -> ctx != null && Boolean.TRUE.equals(ctx.get(expr));

    private ScoreBasedGrader grader() {
        return new ScoreBasedGrader(FLAG_EVAL);
    }

    private static List<GradeBand> bands() {
        // [0,30)->三级, [30,60)->二级, [60,100]->一级（用闭区间相邻，边界归低区间）
        return List.of(
                new GradeBand(bd(0), bd(30), "三级"),
                new GradeBand(bd(30), bd(60), "二级"),
                new GradeBand(bd(60), bd(100), "一级"));
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void accruesHitScores_andSumsTotal() {
        // R12.2/R12.3：命中两项，总分为各计入分值之和
        List<RatingItem> items = List.of(
                RatingItem.of("a", bd(10)),
                RatingItem.of("b", bd(25)),
                RatingItem.of("c", bd(50)));
        Map<String, Object> ctx = Map.of("a", true, "b", true, "c", false);

        ScoreBasedRatingResult r = grader().grade(items, bands(), ctx);

        assertThat(r.totalScore()).isEqualByComparingTo(bd(35));
        assertThat(r.grade()).isEqualTo("二级");
        assertThat(r.hitItems()).hasSize(2);
        assertThat(r.outOfRange()).isFalse();
        assertThat(r.note()).isNull();
    }

    @Test
    void capsSubItemScoreAtSubItemCap() {
        // R12.2：分值 50 但上限 20，计入 20
        List<RatingItem> items = List.of(RatingItem.of("a", bd(50), bd(20)));
        Map<String, Object> ctx = Map.of("a", true);

        ScoreBasedRatingResult r = grader().grade(items, bands(), ctx);

        assertThat(r.totalScore()).isEqualByComparingTo(bd(20));
        assertThat(r.hitItems().get(0).countedScore()).isEqualByComparingTo(bd(20));
        assertThat(r.grade()).isEqualTo("三级");
    }

    @Test
    void mapsTotalToContainingBand() {
        // R12.4：总分 65 落入 [60,100] -> 一级
        List<RatingItem> items = List.of(RatingItem.of("a", bd(65)));
        ScoreBasedRatingResult r = grader().grade(items, bands(), Map.of("a", true));
        assertThat(r.grade()).isEqualTo("一级");
        assertThat(r.outOfRange()).isFalse();
    }

    @Test
    void boundaryScore_goesToLowerBand() {
        // 边界 30 同时属于 [0,30] 与 [30,60)，按升序取首个命中 -> 三级
        List<RatingItem> items = List.of(RatingItem.of("a", bd(30)));
        ScoreBasedRatingResult r = grader().grade(items, bands(), Map.of("a", true));
        assertThat(r.grade()).isEqualTo("三级");
    }

    @Test
    void aboveAllBands_isBoundaryGradeAndOutOfRange() {
        // R12.5：总分 150 越界，定为最高等级并记录越界
        List<RatingItem> items = List.of(RatingItem.of("a", bd(150)));
        ScoreBasedRatingResult r = grader().grade(items, bands(), Map.of("a", true));
        assertThat(r.totalScore()).isEqualByComparingTo(bd(150));
        assertThat(r.grade()).isEqualTo("一级");
        assertThat(r.outOfRange()).isTrue();
    }

    @Test
    void belowAllBands_isBoundaryGradeAndOutOfRange() {
        // R12.5：负分越界（低于最低下界 0），定为最低等级并记录越界
        List<RatingItem> items = List.of(RatingItem.of("a", bd(-5)));
        ScoreBasedRatingResult r = grader().grade(items, bands(), Map.of("a", true));
        assertThat(r.grade()).isEqualTo("三级");
        assertThat(r.outOfRange()).isTrue();
    }

    @Test
    void noHit_totalZero_withChineseNote() {
        // R12.6：未命中任何子项 -> 总分 0、按 0 落区间定级、中文标注
        List<RatingItem> items = List.of(RatingItem.of("a", bd(10)), RatingItem.of("b", bd(20)));
        Map<String, Object> ctx = Map.of("a", false, "b", false);

        ScoreBasedRatingResult r = grader().grade(items, bands(), ctx);

        assertThat(r.totalScore()).isEqualByComparingTo(bd(0));
        assertThat(r.grade()).isEqualTo("三级");
        assertThat(r.hitItems()).isEmpty();
        assertThat(r.note()).isEqualTo(ScoreBasedGrader.NOTE_NO_HIT);
        assertThat(r.outOfRange()).isFalse();
    }

    @Test
    void resultReturnsHitDetailWithCountedValues() {
        // R12.7：结果返回总分、等级与各命中子项计入分值明细
        List<RatingItem> items = List.of(
                new RatingItem("交易", "大额", "a", bd(40), bd(30), "HIGH"));
        ScoreBasedRatingResult r = grader().grade(items, bands(), Map.of("a", true));

        assertThat(r.hitItems()).hasSize(1);
        ScoreBasedRatingResult.HitItem hit = r.hitItems().get(0);
        assertThat(hit.category()).isEqualTo("交易");
        assertThat(hit.subItem()).isEqualTo("大额");
        assertThat(hit.countedScore()).isEqualByComparingTo(bd(30)); // 封顶后
        assertThat(r.totalScore()).isEqualByComparingTo(bd(30));
    }

    @Test
    void evaluationException_treatedAsMiss() {
        RuleExpressionEvaluator boom = (expr, ctx) -> {
            if (expr.equals("boom")) {
                throw new RuntimeException("eval failed");
            }
            return ctx != null && Boolean.TRUE.equals(ctx.get(expr));
        };
        ScoreBasedGrader g = new ScoreBasedGrader(boom);
        List<RatingItem> items = List.of(RatingItem.of("boom", bd(99)), RatingItem.of("a", bd(10)));
        ScoreBasedRatingResult r = g.grade(items, bands(), Map.of("a", true));
        assertThat(r.totalScore()).isEqualByComparingTo(bd(10));
        assertThat(r.hitItems()).hasSize(1);
    }
}
