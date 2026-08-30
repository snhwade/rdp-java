package com.riskplatform.engine.domain.rulepackage;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decision.DecisionAggregator;
import com.riskplatform.engine.domain.rule.ExecutableRule;
import com.riskplatform.engine.domain.rule.HitDecision;
import com.riskplatform.engine.domain.rule.RuleExecutionResult;
import com.riskplatform.engine.domain.rule.RuleExecutor;
import com.riskplatform.engine.domain.score.ScoreCalculator;
import com.riskplatform.engine.domain.strategy.HitRuleStrategies;
import com.riskplatform.engine.domain.strategy.StrategyAggregateResult;
import com.riskplatform.engine.domain.strategy.StrategyAggregator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则包执行器（R1.2/R1.3/R4.4/R4.5）。
 *
 * <p>编排一次规则包执行，复用既有执行内核组件，按触发模式走不同编排：
 *
 * <h3>命中模式（HIT）</h3>
 * <ol>
 *   <li>用 {@link RuleExecutor} 按 (priority desc, ruleId asc) 执行各规则条件表达式并支持短路（R5.1/R5.6）；</li>
 *   <li>收集命中规则集，按命中规则绑定的策略组装 {@link HitRuleStrategies}；</li>
 *   <li>{@link StrategyAggregator#aggregateHit} 聚合命中模式四类策略（R1.2）；</li>
 *   <li>{@link DecisionAggregator#aggregate} 由命中规则决策聚合出规则包决策（最高优先级中取最严格者，R6）；</li>
 *   <li>产出 {@link RulePackageResult}（score=null，warnGenerated=false，riskLevelCode=null）。</li>
 * </ol>
 *
 * <h3>评分模式（SCORE）</h3>
 * <ol>
 *   <li>用 {@link RuleExecutor} 执行各规则条件表达式确定哪些规则被触发（强制关闭短路，收集全部触发规则）；</li>
 *   <li>对每条被触发的规则用 {@link ScoreCalculator#triggerScore} 计算触发分（基础分 + 动态分），累加得总分（R4.3/R4.4）；</li>
 *   <li>{@link StrategyAggregator#aggregateScore} 按总分映射唯一分值区间，得到风险等级与区间策略（R1.3）；</li>
 *   <li>预警单阈值判定：当 {@code warnScoreEnabled} 且配置了运算符/阈值时，按 GTE/LT 判定是否生成风控结果（R4.5）；</li>
 *   <li>决策结论由风险等级经 {@link RiskLevelDecisionMapper} 映射推导（见下「decision 映射约定」）；</li>
 *   <li>产出 {@link RulePackageResult}（含 score、riskLevelCode、strategies、warnGenerated）。</li>
 * </ol>
 *
 * <h3>decision 映射约定</h3>
 * <ul>
 *   <li><b>命中模式</b>：decision 来源于命中规则的决策聚合（{@link DecisionAggregator}）——取决策优先级
 *       数值最大者，并在其中取严格性最高者（REJECT&gt;REVIEW&gt;PASS）；无命中则 PASS（R6.4）。</li>
 *   <li><b>评分模式</b>：decision 由命中分值区间的风险等级经 {@link RiskLevelDecisionMapper} 推导，
 *       未命中任何区间默认 PASS。注意：预警单标志 {@code warnGenerated} 与 decision 相互独立——
 *       decision 表达决策流可消费的统一结论，warnGenerated 仅表达「是否生成预警单（风控结果）」。</li>
 * </ul>
 *
 * <p>本类为可注入组件：依赖 {@link RuleExecutor}/{@link StrategyAggregator}/{@link ScoreCalculator}/
 * {@link RiskLevelDecisionMapper}/{@link DecisionAggregator} 均由构造器注入，便于复用与测试。
 */
public class RulePackageExecutor {

    private final RuleExecutor ruleExecutor;
    private final StrategyAggregator strategyAggregator;
    private final ScoreCalculator scoreCalculator;
    private final DecisionAggregator decisionAggregator;
    private final RiskLevelDecisionMapper riskLevelDecisionMapper;

    /**
     * 全量注入构造器。
     *
     * @param ruleExecutor            规则执行内核（命中/触发判定 + 短路）
     * @param strategyAggregator      策略聚合器（命中模式四类聚合 / 评分模式区间映射）
     * @param scoreCalculator         评分计算器（单规则触发分）
     * @param decisionAggregator      决策聚合器（命中模式决策推导）
     * @param riskLevelDecisionMapper 风险等级→决策映射（评分模式决策推导）
     */
    public RulePackageExecutor(RuleExecutor ruleExecutor,
                               StrategyAggregator strategyAggregator,
                               ScoreCalculator scoreCalculator,
                               DecisionAggregator decisionAggregator,
                               RiskLevelDecisionMapper riskLevelDecisionMapper) {
        this.ruleExecutor = ruleExecutor;
        this.strategyAggregator = strategyAggregator;
        this.scoreCalculator = scoreCalculator;
        this.decisionAggregator = decisionAggregator;
        this.riskLevelDecisionMapper = riskLevelDecisionMapper;
    }

    /**
     * 便捷构造器：使用默认 {@link DecisionAggregator} 与默认风险等级映射约定。
     */
    public RulePackageExecutor(RuleExecutor ruleExecutor,
                               StrategyAggregator strategyAggregator,
                               ScoreCalculator scoreCalculator) {
        this(ruleExecutor, strategyAggregator, scoreCalculator,
                new DecisionAggregator(), RiskLevelDecisionMapper.DEFAULT);
    }

    /**
     * 执行规则包。
     *
     * @param definition 规则包执行定义（含触发模式、规则、分值区间、预警阈值）
     * @param context    决策上下文（事件字段 + 指标当前值的合并视图）
     * @return 规则包执行结果
     */
    public RulePackageResult execute(RulePackageDefinition definition, Map<String, Object> context) {
        if (definition.triggerMode() == TriggerMode.SCORE) {
            return executeScore(definition, context);
        }
        return executeHit(definition, context);
    }

    /**
     * 命中模式编排（R1.2）。
     *
     * <p>规则三态隔离（R7.6）：命中明细 {@code hits} 含全部被执行规则（上线 + 试运行）的命中，
     * 但策略聚合与决策聚合<strong>仅纳入上线（非试运行）命中</strong>，试运行命中仅供观察、不影响
     * 最终决策。
     */
    private RulePackageResult executeHit(RulePackageDefinition definition, Map<String, Object> context) {
        Map<Long, RulePackageRule> ruleById = indexById(definition.rules());

        // 1. 规则执行 + 短路（命中模式保留各规则自身短路设置）
        List<ExecutableRule> executable = definition.rules().stream()
                .map(r -> r.toExecutableRule(false))
                .toList();
        RuleExecutionResult execResult = ruleExecutor.execute(executable, context);
        List<HitDecision> hits = execResult.hitDecisions();

        // 2. 收集命中规则绑定的策略 -> 命中模式策略聚合（仅上线命中纳入，R7.6）
        List<HitRuleStrategies> hitRuleStrategies = new ArrayList<>();
        for (HitDecision hit : hits) {
            if (hit.trialRun()) {
                continue;
            }
            RulePackageRule rule = ruleById.get(hit.ruleId());
            if (rule != null) {
                hitRuleStrategies.add(new HitRuleStrategies(rule.ruleId(), rule.strategies()));
            }
        }
        StrategyAggregateResult aggregated = strategyAggregator.aggregateHit(hitRuleStrategies);

        // 3. 决策聚合：仅以上线命中聚合（试运行命中不参与，R7.6）；无上线命中则 PASS
        Decision decision = decisionAggregator.aggregate(onlineHits(hits));

        return new RulePackageResult(TriggerMode.HIT, decision, hits,
                null, null, aggregated.strategies(), false,
                execResult.records(), execResult.status());
    }

    /**
     * 评分模式编排（R1.3/R4.4/R4.5）。
     *
     * <p>规则三态隔离（R7.6）：命中明细 {@code triggered} 含全部被触发规则（上线 + 试运行），
     * 但总分累加<strong>仅纳入上线（非试运行）触发规则</strong>，试运行触发仅供观察、不影响总分与
     * 由总分推导的最终决策。
     */
    private RulePackageResult executeScore(RulePackageDefinition definition, Map<String, Object> context) {
        Map<Long, RulePackageRule> ruleById = indexById(definition.rules());

        // 1. 执行规则确定哪些被触发（强制关闭短路，收集全部触发规则）
        List<ExecutableRule> executable = definition.rules().stream()
                .map(r -> r.toExecutableRule(true))
                .toList();
        RuleExecutionResult execResult = ruleExecutor.execute(executable, context);
        List<HitDecision> triggered = execResult.hitDecisions();

        // 2. 累加各触发规则触发分（基础分 + 动态分）；试运行触发不计入总分（R7.6）
        BigDecimal totalScore = BigDecimal.ZERO;
        for (HitDecision hit : triggered) {
            if (hit.trialRun()) {
                continue;
            }
            RulePackageRule rule = ruleById.get(hit.ruleId());
            if (rule != null && rule.scoreRule() != null) {
                totalScore = totalScore.add(scoreCalculator.triggerScore(rule.scoreRule(), context));
            }
        }

        // 3. 总分映射唯一分值区间 -> 风险等级 + 区间策略
        StrategyAggregateResult aggregated =
                strategyAggregator.aggregateScore(totalScore, definition.scoreBands());
        String riskLevelCode = aggregated.riskLevelCode();

        // 4. 预警单阈值判定（R4.5）：开启且配置完整时按 GTE/LT 判定
        boolean warnGenerated = evaluateWarn(definition, totalScore);

        // 5. 决策结论由风险等级映射推导（见类注释「decision 映射约定」）
        Decision decision = riskLevelDecisionMapper.toDecision(riskLevelCode);

        return new RulePackageResult(TriggerMode.SCORE, decision, triggered,
                totalScore, riskLevelCode, aggregated.strategies(), warnGenerated,
                execResult.records(), execResult.status());
    }

    /**
     * 预警单阈值判定：仅当开启预警且运算符/阈值齐备时按运算符判定，否则不生成（R4.5）。
     */
    private boolean evaluateWarn(RulePackageDefinition definition, BigDecimal totalScore) {
        if (!definition.warnScoreEnabled()
                || definition.warnScoreOp() == null
                || definition.warnScoreThreshold() == null) {
            return false;
        }
        return definition.warnScoreOp().test(totalScore, definition.warnScoreThreshold());
    }

    /** 按 ruleId 建立索引，便于命中后回查规则的策略/评分配置。 */
    private Map<Long, RulePackageRule> indexById(List<RulePackageRule> rules) {
        Map<Long, RulePackageRule> map = new LinkedHashMap<>();
        for (RulePackageRule rule : rules) {
            map.put(rule.ruleId(), rule);
        }
        return map;
    }

    /** 仅保留上线（非试运行）命中，供最终决策聚合使用（试运行隔离，R7.6）。 */
    private List<HitDecision> onlineHits(List<HitDecision> hits) {
        List<HitDecision> online = new ArrayList<>(hits.size());
        for (HitDecision hit : hits) {
            if (!hit.trialRun()) {
                online.add(hit);
            }
        }
        return online;
    }
}
