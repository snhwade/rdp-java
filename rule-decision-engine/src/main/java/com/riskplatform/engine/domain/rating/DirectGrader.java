package com.riskplatform.engine.domain.rating;

import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 直接定级器（引擎执行侧，核心，R13.2–R13.6）。
 *
 * <p>对一个评级模型的直接定级项 {@link DirectGradingItem}，在给定决策上下文与等级序 {@link GradeOrder} 下完成定级：
 * <ol>
 *   <li>仅命中一项 → 该项等级（R13.2）；</li>
 *   <li>命中多项同级 → 该等级（R13.3）；</li>
 *   <li>命中多项异级 → 最高等级（依据等级序，R13.4）；</li>
 *   <li>未命中任何项 → 未定级结果（{@link #UNGRADED}，R13.5）；</li>
 *   <li>结果返回所得等级与全部命中定级项（R13.6）。</li>
 * </ol>
 *
 * <p>条件求值复用 {@link RuleExpressionEvaluator}（基础设施层以 Aviator 实现），与规则条件求值同构。
 * 求值异常视为未命中（不计入），保证单个异常定级项不影响整体定级的确定性。
 *
 * <p>等级序由 {@link GradeOrder} 提供（通常自评级模型的等级区间 {@link GradeBand} 列表构建）。
 * 多项异级命中时调用 {@link GradeOrder#highest(java.util.Collection)} 取最高等级。
 *
 * <p>无状态、确定性：相同输入恒得相同结果，可直接用于属性测试（任务 15.3）。
 */
public class DirectGrader {

    /** 未命中任何定级项时的中文标注（R13.5）。 */
    public static final String UNGRADED = "未定级";

    private final RuleExpressionEvaluator conditionEvaluator;

    public DirectGrader(RuleExpressionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * 执行直接定级。
     *
     * @param items      直接定级项列表
     * @param gradeOrder 等级序（用于多项异级命中时取最高等级，R13.4）
     * @param context    决策上下文（条件求值环境）
     * @return 直接定级结果
     */
    public DirectGradingResult grade(List<DirectGradingItem> items,
                                     GradeOrder gradeOrder,
                                     Map<String, Object> context) {
        List<DirectGradingItem> safeItems = items == null ? List.of() : items;
        Map<String, Object> env = context == null ? Map.of() : context;

        // 逐项判定命中，保留全部命中项（按输入顺序，R13.6）
        List<DirectGradingItem> hitItems = new ArrayList<>();
        Set<String> hitGrades = new LinkedHashSet<>();
        for (DirectGradingItem item : safeItems) {
            if (isHit(item.condition(), env)) {
                hitItems.add(item);
                hitGrades.add(item.grade());
            }
        }

        // R13.5：未命中任何项 → 未定级
        if (hitItems.isEmpty()) {
            return new DirectGradingResult(UNGRADED, false, List.of());
        }

        // R13.2 单项命中 / R13.3 多项同级 / R13.4 多项异级取最高等级。
        // hitGrades 去重后，单一等级（含单项命中、多项同级）直接取该等级；
        // 多个不同等级依据等级序取最高（GradeOrder.highest 对单元素集合亦返回该元素）。
        String resultGrade = gradeOrder == null
                ? hitItems.get(0).grade()
                : gradeOrder.highest(hitGrades);

        return new DirectGradingResult(resultGrade, true, hitItems);
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
}
