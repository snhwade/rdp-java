package com.riskplatform.engine.domain.rating;

import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 直接定级器单元测试（R13.2–R13.6）。
 *
 * <p>条件求值器以"上下文中布尔标志"模拟 Aviator：表达式即上下文键名，值为 true 即命中，
 * 便于在不依赖具体表达式引擎下验证单项/多项同级/多项异级/未命中等定级语义。
 */
class DirectGraderTest {

    /** 简化条件求值：表达式作为上下文键，值为 Boolean.TRUE 即命中。 */
    private static final RuleExpressionEvaluator FLAG_EVAL =
            (expr, ctx) -> ctx != null && Boolean.TRUE.equals(ctx.get(expr));

    private DirectGrader grader() {
        return new DirectGrader(FLAG_EVAL);
    }

    /** 等级序（低→高）：三级 < 二级 < 一级。 */
    private static GradeOrder order() {
        return GradeOrder.fromBands(List.of(
                new GradeBand(bd(0), bd(30), "三级"),
                new GradeBand(bd(30), bd(60), "二级"),
                new GradeBand(bd(60), bd(100), "一级")));
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void singleHitReturnsThatGrade() {
        // R13.2：仅命中一项 → 该项等级
        List<DirectGradingItem> items = List.of(
                DirectGradingItem.of("a", "二级"),
                DirectGradingItem.of("b", "一级"));
        DirectGradingResult r = grader().grade(items, order(), Map.of("a", true));

        assertThat(r.graded()).isTrue();
        assertThat(r.grade()).isEqualTo("二级");
        assertThat(r.hitItems()).extracting(DirectGradingItem::grade).containsExactly("二级");
    }

    @Test
    void multipleHitSameGradeReturnsThatGrade() {
        // R13.3：命中多项同级 → 该等级
        List<DirectGradingItem> items = List.of(
                DirectGradingItem.of("a", "二级"),
                DirectGradingItem.of("b", "二级"),
                DirectGradingItem.of("c", "一级"));
        DirectGradingResult r = grader().grade(items, order(), Map.of("a", true, "b", true));

        assertThat(r.graded()).isTrue();
        assertThat(r.grade()).isEqualTo("二级");
        assertThat(r.hitItems()).hasSize(2);
    }

    @Test
    void multipleHitDifferentGradesReturnsHighest() {
        // R13.4：命中多项异级 → 最高等级（依据等级序）
        List<DirectGradingItem> items = List.of(
                DirectGradingItem.of("a", "三级"),
                DirectGradingItem.of("b", "一级"),
                DirectGradingItem.of("c", "二级"));
        DirectGradingResult r = grader().grade(items, order(), Map.of("a", true, "b", true, "c", true));

        assertThat(r.graded()).isTrue();
        assertThat(r.grade()).isEqualTo("一级");
        // R13.6：返回全部命中定级项
        assertThat(r.hitItems()).hasSize(3);
    }

    @Test
    void noHitReturnsUngraded() {
        // R13.5：未命中任何项 → 未定级
        List<DirectGradingItem> items = List.of(
                DirectGradingItem.of("a", "二级"),
                DirectGradingItem.of("b", "一级"));
        DirectGradingResult r = grader().grade(items, order(), Map.of("a", false));

        assertThat(r.graded()).isFalse();
        assertThat(r.grade()).isEqualTo(DirectGrader.UNGRADED);
        assertThat(r.hitItems()).isEmpty();
    }

    @Test
    void evaluationExceptionTreatedAsNoHit() {
        // 求值异常按未命中处理，不影响其它项定级
        RuleExpressionEvaluator boom = (expr, ctx) -> {
            if ("boom".equals(expr)) {
                throw new IllegalStateException("eval error");
            }
            return ctx != null && Boolean.TRUE.equals(ctx.get(expr));
        };
        DirectGrader g = new DirectGrader(boom);
        List<DirectGradingItem> items = List.of(
                DirectGradingItem.of("boom", "一级"),
                DirectGradingItem.of("a", "二级"));
        DirectGradingResult r = g.grade(items, order(), Map.of("a", true));

        assertThat(r.graded()).isTrue();
        assertThat(r.grade()).isEqualTo("二级");
        assertThat(r.hitItems()).extracting(DirectGradingItem::grade).containsExactly("二级");
    }
}
