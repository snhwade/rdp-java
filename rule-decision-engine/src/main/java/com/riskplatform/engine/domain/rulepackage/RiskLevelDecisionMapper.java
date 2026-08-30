package com.riskplatform.engine.domain.rulepackage;

import com.riskplatform.engine.domain.decision.Decision;

/**
 * 评分模式风险等级 → 决策结论映射（R1.3/R4.4）。
 *
 * <p>评分模式本身产出的是「总分 + 风险等级 + 区间策略」，而决策流需要统一的
 * {@link Decision} 结论以便并入累计结果与决策聚合。本接口将命中区间的风险等级编码映射为决策。
 *
 * <p>可注入自定义实现以贴合各机构的风险等级字典（risk_level 字典 code → 决策）。
 */
@FunctionalInterface
public interface RiskLevelDecisionMapper {

    /**
     * 将风险等级编码映射为决策结论。
     *
     * @param riskLevelCode 命中分值区间对应的风险等级编码（可能为 null，表示未命中任何区间）
     * @return 决策结论
     */
    Decision toDecision(String riskLevelCode);

    /**
     * 默认映射约定（保守策略）：
     * <ul>
     *   <li>未命中任何分值区间（riskLevelCode 为 null/空）→ {@link Decision#PASS PASS}；</li>
     *   <li>命中区间且风险等级编码（不区分大小写）以 {@code HIGH}/{@code H}/{@code REJECT}/{@code BLOCK}
     *       开头 → {@link Decision#REJECT REJECT}；</li>
     *   <li>命中区间且以 {@code LOW}/{@code L}/{@code PASS} 开头 → {@link Decision#PASS PASS}；</li>
     *   <li>其余命中区间（如中风险或未识别等级）→ {@link Decision#REVIEW REVIEW}（保守转人工）。</li>
     * </ul>
     *
     * <p>该约定为「无外部风险等级字典」时的兜底；接入具体字典时应注入自定义映射覆盖。
     */
    RiskLevelDecisionMapper DEFAULT = riskLevelCode -> {
        if (riskLevelCode == null || riskLevelCode.isBlank()) {
            return Decision.PASS;
        }
        String code = riskLevelCode.trim().toUpperCase();
        if (code.startsWith("HIGH") || code.startsWith("REJECT") || code.startsWith("BLOCK")
                || code.equals("H")) {
            return Decision.REJECT;
        }
        if (code.startsWith("LOW") || code.startsWith("PASS") || code.equals("L")) {
            return Decision.PASS;
        }
        return Decision.REVIEW;
    };
}
