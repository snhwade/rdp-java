package com.riskplatform.engine.domain.rating;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评分定级结果（引擎执行侧，R12.7）。
 *
 * <p>承载一次评分定级的产出：总分 {@link #totalScore}、所得等级 {@link #grade}、各命中评级子项
 * 及其计入分值 {@link #hitItems}，以及两个语义标记：
 * <ul>
 *   <li>{@link #outOfRange}：总分越界（超出所有等级区间覆盖范围），此时按边界等级定级（R12.5）。</li>
 *   <li>{@link #note}：中文标注，未命中任何子项时为"未命中任何子项"（R12.6），否则为 null。</li>
 * </ul>
 *
 * @param totalScore 总分（各命中子项计入分值之和，R12.3）
 * @param grade      所得等级（无任何等级区间时可能为 null）
 * @param hitItems   命中子项明细（含每项计入分值，R12.7）
 * @param outOfRange 总分是否越界（R12.5）
 * @param note       中文标注（未命中任何子项时为"未命中任何子项"，R12.6；否则 null）
 */
public record ScoreBasedRatingResult(BigDecimal totalScore,
                                     String grade,
                                     List<HitItem> hitItems,
                                     boolean outOfRange,
                                     String note) {

    public ScoreBasedRatingResult {
        hitItems = hitItems == null ? List.of() : List.copyOf(hitItems);
    }

    /**
     * 命中评级子项明细。
     *
     * @param category    评级类别（回显）
     * @param subItem     评级子项名（回显）
     * @param condition   命中条件
     * @param countedScore 计入分值（已封顶，min(分值, 上限)，R12.2）
     */
    public record HitItem(String category, String subItem, String condition, BigDecimal countedScore) {
    }
}
