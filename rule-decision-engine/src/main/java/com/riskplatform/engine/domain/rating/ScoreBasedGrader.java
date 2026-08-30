package com.riskplatform.engine.domain.rating;

import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 评分定级器（引擎执行侧，核心，R12.2–R12.7）。
 *
 * <p>对一个评级模型的评级子项 {@link RatingItem} 与等级区间 {@link GradeBand}，在给定决策上下文下完成定级：
 * <ol>
 *   <li>对每个条件命中的子项计入分值，按子项分值上限封顶（计入值 = min(分值, 上限)，R12.2）；</li>
 *   <li>总分 = 各命中子项计入分值之和（R12.3）；</li>
 *   <li>总分落入等级区间则得到对应等级（R12.4）；</li>
 *   <li>总分越界（超出所有区间覆盖范围）则定为边界等级并记录越界（R12.5）；</li>
 *   <li>未命中任何子项时总分为 0，按 0 所落区间定级，并以中文标注"未命中任何子项"（R12.6）；</li>
 *   <li>结果返回总分、所得等级与各命中子项及其计入分值（R12.7）。</li>
 * </ol>
 *
 * <p>条件求值复用 {@link RuleExpressionEvaluator}（基础设施层以 Aviator 实现），与规则条件求值同构。
 * 求值异常视为未命中（不计入），保证单个异常子项不影响整体定级的确定性。
 *
 * <p>无状态、确定性：相同输入恒得相同结果，可直接用于属性测试（任务 15.3）。
 */
public class ScoreBasedGrader {

    /** 未命中任何子项时的中文标注（R12.6）。 */
    public static final String NOTE_NO_HIT = "未命中任何子项";

    private final RuleExpressionEvaluator conditionEvaluator;

    public ScoreBasedGrader(RuleExpressionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * 执行评分定级。
     *
     * @param items   评级子项列表
     * @param bands   等级区间列表（顺序不限，内部按下界升序归一）
     * @param context 决策上下文（条件求值环境）
     * @return 评分定级结果
     */
    public ScoreBasedRatingResult grade(List<RatingItem> items,
                                        List<GradeBand> bands,
                                        Map<String, Object> context) {
        List<RatingItem> safeItems = items == null ? List.of() : items;
        Map<String, Object> env = context == null ? Map.of() : context;

        // 1) 逐子项判定命中并计入封顶分值（R12.2）
        List<ScoreBasedRatingResult.HitItem> hitItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (RatingItem item : safeItems) {
            if (isHit(item.condition(), env)) {
                BigDecimal counted = item.cappedScore();
                total = total.add(counted); // 2) 总分累加（R12.3）
                hitItems.add(new ScoreBasedRatingResult.HitItem(
                        item.category(), item.subItem(), item.condition(), counted));
            }
        }

        boolean noHit = hitItems.isEmpty();
        // R12.6：未命中任何子项时总分为 0
        String note = noHit ? NOTE_NO_HIT : null;

        // 3/4) 总分到等级映射；越界定为边界等级并记录越界（R12.4/R12.5）
        List<GradeBand> sorted = sortedBands(bands);
        GradeResolution resolution = resolveGrade(total, sorted);

        return new ScoreBasedRatingResult(total, resolution.grade, hitItems, resolution.outOfRange, note);
    }

    private boolean isHit(String condition, Map<String, Object> context) {
        if (condition == null || condition.isBlank()) {
            return false;
        }
        try {
            return conditionEvaluator.evaluate(condition, context);
        } catch (RuntimeException e) {
            // 求值异常按未命中处理，保证整体定级确定性
            return false;
        }
    }

    private List<GradeBand> sortedBands(List<GradeBand> bands) {
        List<GradeBand> sorted = new ArrayList<>(bands == null ? List.of() : bands);
        sorted.sort(Comparator.comparing(GradeBand::minScore));
        return sorted;
    }

    /**
     * 总分到等级的解析：
     * <ul>
     *   <li>落入某区间 → 取首个包含该总分的区间等级（区间已按下界升序，边界归入低区间，R12.4）；</li>
     *   <li>低于最低区间下界 → 越界，定为最低等级（边界等级，R12.5）；</li>
     *   <li>高于最高区间上界 → 越界，定为最高等级（边界等级，R12.5）；</li>
     *   <li>无任何区间 → 等级 null，不计越界。</li>
     * </ul>
     */
    private GradeResolution resolveGrade(BigDecimal total, List<GradeBand> sorted) {
        if (sorted.isEmpty()) {
            return new GradeResolution(null, false);
        }
        for (GradeBand band : sorted) {
            if (band.contains(total)) {
                return new GradeResolution(band.grade(), false);
            }
        }
        // 越界：定为最近的边界等级（R12.5）
        GradeBand lowest = sorted.get(0);
        if (total.compareTo(lowest.minScore()) < 0) {
            return new GradeResolution(lowest.grade(), true);
        }
        GradeBand highest = sorted.get(sorted.size() - 1);
        return new GradeResolution(highest.grade(), true);
    }

    private record GradeResolution(String grade, boolean outOfRange) {
    }
}
