package com.riskplatform.engine.domain.rulepackage;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rule.GroupExecutionStatus;
import com.riskplatform.engine.domain.rule.HitDecision;
import com.riskplatform.engine.domain.rule.RuleExecutionRecord;
import com.riskplatform.engine.domain.strategy.StrategyItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则包执行结果（命中/评分两模式共用，R1.2/R1.3/R4.4/R4.5）。
 *
 * <p>由 {@link RulePackageExecutor} 编排产出，供决策流「规则包节点」并入累计结果（R6.2）。
 *
 * <p><b>字段按模式使用：</b>
 * <ul>
 *   <li>命中模式：{@link #decision} 由命中规则做决策聚合得到；{@link #hitRules} 为命中规则决策；
 *       {@link #strategies} 为命中模式策略聚合输出；{@link #score}/{@link #warnGenerated} 不适用
 *       （score 为 null，warnGenerated 为 false）；{@link #riskLevelCode} 为 null。</li>
 *   <li>评分模式：{@link #score} 为各触发规则触发分累加的总分；{@link #riskLevelCode} 与
 *       {@link #strategies} 由总分映射的分值区间得到；{@link #warnGenerated} 为预警单阈值判定结果；
 *       {@link #hitRules} 为被触发（命中）规则的决策记录；{@link #decision} 由风险等级/区间映射推导
 *       （见 {@link RulePackageExecutor} 的映射约定说明）。</li>
 * </ul>
 *
 * @param triggerMode   触发模式
 * @param decision      规则包决策结论
 * @param hitRules      命中/触发规则的决策记录
 * @param score         评分模式总分（命中模式为 null）
 * @param riskLevelCode 风险等级编码（评分模式命中区间时给出；命中模式为 null）
 * @param strategies    聚合后输出的策略列表
 * @param warnGenerated 评分模式预警单标志（是否生成风控结果；命中模式恒为 false）
 * @param executionRecords 全部规则执行记录（含未命中/失败，R5.5/XT1）
 * @param groupStatus 规则组执行状态（R5.4）
 */
public record RulePackageResult(TriggerMode triggerMode,
                                Decision decision,
                                List<HitDecision> hitRules,
                                BigDecimal score,
                                String riskLevelCode,
                                List<StrategyItem> strategies,
                                boolean warnGenerated,
                                List<RuleExecutionRecord> executionRecords,
                                GroupExecutionStatus groupStatus) {

    public RulePackageResult(TriggerMode triggerMode,
                             Decision decision,
                             List<HitDecision> hitRules,
                             BigDecimal score,
                             String riskLevelCode,
                             List<StrategyItem> strategies,
                             boolean warnGenerated) {
        this(triggerMode, decision, hitRules, score, riskLevelCode, strategies, warnGenerated,
                List.of(), GroupExecutionStatus.COMPLETED);
    }

    public RulePackageResult {
        hitRules = hitRules == null ? List.of() : List.copyOf(hitRules);
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
        executionRecords = executionRecords == null ? List.of() : List.copyOf(executionRecords);
        groupStatus = groupStatus == null ? GroupExecutionStatus.COMPLETED : groupStatus;
    }
}
