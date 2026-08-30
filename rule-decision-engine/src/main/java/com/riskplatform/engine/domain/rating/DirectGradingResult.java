package com.riskplatform.engine.domain.rating;

import java.util.List;

/**
 * 直接定级结果（引擎执行侧，R13.5/R13.6）。
 *
 * <p>承载一次直接定级的产出：所得等级 {@link #grade} 与全部命中定级项 {@link #hitItems}。
 * 语义对应需求：
 * <ul>
 *   <li>仅命中一项 → 该项等级（R13.2）；</li>
 *   <li>命中多项同级 → 该等级（R13.3）；</li>
 *   <li>命中多项异级 → 最高等级（依据等级序 {@link GradeOrder}，R13.4）；</li>
 *   <li>未命中任何项 → 未定级（{@link #graded} 为 false，{@link #grade} 为 {@link DirectGrader#UNGRADED}，R13.5）。</li>
 * </ul>
 *
 * @param grade    所得等级；未命中时为 {@link DirectGrader#UNGRADED}（中文"未定级"）
 * @param graded   是否定级成功（命中至少一项为 true；未命中为 false，R13.5）
 * @param hitItems 全部命中定级项（按输入顺序，R13.6）
 */
public record DirectGradingResult(String grade, boolean graded, List<DirectGradingItem> hitItems) {

    public DirectGradingResult {
        hitItems = hitItems == null ? List.of() : List.copyOf(hitItems);
    }
}
