package com.riskplatform.engine.domain.rulepackage;

import com.riskplatform.engine.domain.strategy.ScoreBand;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则包执行定义（引擎运行面轻量 DTO，R1.2/R1.3/R4.4/R4.5）。
 *
 * <p>封装一次规则包执行编排所需的全部静态配置：触发模式、规则列表（已编译表达式 + 绑定策略 /
 * 评分配置）、评分模式分值区间、以及评分模式的预警单阈值配置。
 *
 * <p><b>评分模式分值区间</b>{@link #scoreBands} 复用策略侧 {@link ScoreBand}（区间不重叠、可含负分，
 * 命中区间输出风险等级与区间策略）。<b>预警单阈值</b>由 {@link #warnScoreEnabled} 开关 +
 * {@link #warnScoreOp}（GTE/LT）+ {@link #warnScoreThreshold} 共同决定（R4.5）。
 *
 * @param packageId         规则包标识（追溯用）
 * @param triggerMode       触发模式（HIT/SCORE，创建后不可变 R1.1）
 * @param rules             规则包内规则列表
 * @param scoreBands        评分模式分值区间（命中模式可空/忽略）
 * @param warnScoreEnabled  评分模式：是否开启预警单分值阈值（R4.5）
 * @param warnScoreOp       评分模式：预警单阈值运算符（GTE/LT；未开启时可为 null）
 * @param warnScoreThreshold 评分模式：预警单阈值（未开启时可为 null）
 */
public record RulePackageDefinition(Long packageId,
                                    TriggerMode triggerMode,
                                    List<RulePackageRule> rules,
                                    List<ScoreBand> scoreBands,
                                    boolean warnScoreEnabled,
                                    WarnScoreOp warnScoreOp,
                                    BigDecimal warnScoreThreshold) {

    public RulePackageDefinition {
        if (triggerMode == null) {
            throw new IllegalArgumentException("触发模式不能为空");
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
        scoreBands = scoreBands == null ? List.of() : List.copyOf(scoreBands);
    }

    /** 命中模式定义快捷构造。 */
    public static RulePackageDefinition hit(Long packageId, List<RulePackageRule> rules) {
        return new RulePackageDefinition(packageId, TriggerMode.HIT, rules,
                List.of(), false, null, null);
    }

    /** 评分模式定义快捷构造（不带预警单阈值）。 */
    public static RulePackageDefinition score(Long packageId,
                                              List<RulePackageRule> rules,
                                              List<ScoreBand> scoreBands) {
        return new RulePackageDefinition(packageId, TriggerMode.SCORE, rules, scoreBands,
                false, null, null);
    }

    /** 评分模式定义快捷构造（带预警单阈值）。 */
    public static RulePackageDefinition score(Long packageId,
                                              List<RulePackageRule> rules,
                                              List<ScoreBand> scoreBands,
                                              WarnScoreOp warnScoreOp,
                                              BigDecimal warnScoreThreshold) {
        return new RulePackageDefinition(packageId, TriggerMode.SCORE, rules, scoreBands,
                true, warnScoreOp, warnScoreThreshold);
    }
}
