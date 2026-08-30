package com.riskplatform.engine.infrastructure.rulepackage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinition;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinitionPort;
import com.riskplatform.engine.domain.rulepackage.RulePackageRule;
import com.riskplatform.engine.domain.rulepackage.RuleRuntimeStatus;
import com.riskplatform.engine.domain.rulepackage.TriggerMode;
import com.riskplatform.engine.domain.rulepackage.WarnScoreOp;
import com.riskplatform.engine.domain.score.ScoreDynamicBand;
import com.riskplatform.engine.domain.score.ScoreRule;
import com.riskplatform.engine.domain.strategy.ScoreBand;
import com.riskplatform.engine.domain.strategy.StrategyCategory;
import com.riskplatform.engine.domain.strategy.StrategyItem;
import com.riskplatform.engine.infrastructure.dryrun.RuleDynamicScoreMapper;
import com.riskplatform.engine.infrastructure.dryrun.RuleDynamicScorePO;
import com.riskplatform.engine.infrastructure.dryrun.RulePackagePO;
import com.riskplatform.engine.infrastructure.dryrun.RulePackageReadMapper;
import com.riskplatform.engine.infrastructure.dryrun.RulePackageRulePO;
import com.riskplatform.engine.infrastructure.dryrun.RulePackageRuleReadMapper;
import com.riskplatform.engine.infrastructure.dryrun.RulePackageScoreBandPO;
import com.riskplatform.engine.infrastructure.dryrun.RulePackageScoreBandReadMapper;
import com.riskplatform.engine.infrastructure.dryrun.RuleV2SampleMapper;
import com.riskplatform.engine.infrastructure.dryrun.RuleV2SamplePO;
import com.riskplatform.engine.infrastructure.rulepackage.StrategyReadMappers.RuleStrategyReadMapper;
import com.riskplatform.engine.infrastructure.rulepackage.StrategyReadMappers.ScoreBandStrategyReadMapper;
import com.riskplatform.engine.infrastructure.rulepackage.StrategyReadMappers.StrategyDefReadMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则包执行定义在线加载适配器（扩展阶段 R6.2）。
 *
 * <p>从 MySQL（rule-config 拥有的表，引擎共享同一库）加载规则包<strong>完整</strong>执行定义，
 * 统一为 {@link RulePackageDefinition} 供决策流「规则包节点」用 {@code RulePackageExecutor} 执行。
 * 与 {@code DbDryRunTargetAdapter} 同款只读 DAO 思路（参考 7.2），区别在于：
 * <ul>
 *   <li>试运行不输出真实策略（R5.2），加载时置空策略；</li>
 *   <li>在线规则包节点需把命中规则/区间映射的策略<strong>一并并入决策流累计结果</strong>（R6.2），
 *       因此本适配器加载 rule_strategy（规则绑定策略）与 score_band_strategy（区间绑定策略）。</li>
 * </ul>
 *
 * <p>读取链路（与 V14/V15/V16 表对齐）：
 * <pre>
 *   rule_package                → 触发模式 / 预警单阈值
 *   rule_package_rule           → 包内规则关联 + 包内优先级
 *   rule_v2 + rule_dynamic_score→ 各规则编译表达式 / 决策语义 / 评分配置
 *   rule_strategy + strategy_def→ 命中模式：规则绑定策略
 *   rule_package_score_band     → 评分模式：分值区间
 *   score_band_strategy + strategy_def → 评分模式：区间绑定策略
 * </pre>
 *
 * <p><b>策略 params_json 约定</b>：限额管控策略的 {@code limitType}/{@code threshold} 从 params_json
 * 读取（键名 limitType/threshold，缺省置空）；其余参数整体作为 {@link StrategyItem#params()} 透传，
 * 与「只记录不下发」边界一致。
 */
@Component
public class DbRulePackageDefinitionAdapter implements RulePackageDefinitionPort {

    private static final Logger log = LoggerFactory.getLogger(DbRulePackageDefinitionAdapter.class);

    private final RulePackageReadMapper packageMapper;
    private final RulePackageRuleReadMapper packageRuleMapper;
    private final RuleV2SampleMapper ruleMapper;
    private final RuleDynamicScoreMapper dynamicScoreMapper;
    private final RulePackageScoreBandReadMapper scoreBandMapper;
    private final StrategyDefReadMapper strategyDefMapper;
    private final RuleStrategyReadMapper ruleStrategyMapper;
    private final ScoreBandStrategyReadMapper scoreBandStrategyMapper;
    private final ObjectMapper objectMapper;
    private final com.riskplatform.engine.infrastructure.configcache.ConfigCacheRegistry configCache;

    public DbRulePackageDefinitionAdapter(RulePackageReadMapper packageMapper,
                                          RulePackageRuleReadMapper packageRuleMapper,
                                          RuleV2SampleMapper ruleMapper,
                                          RuleDynamicScoreMapper dynamicScoreMapper,
                                          RulePackageScoreBandReadMapper scoreBandMapper,
                                          StrategyDefReadMapper strategyDefMapper,
                                          RuleStrategyReadMapper ruleStrategyMapper,
                                          ScoreBandStrategyReadMapper scoreBandStrategyMapper,
                                          ObjectMapper objectMapper,
                                          com.riskplatform.engine.infrastructure.configcache.ConfigCacheRegistry configCache) {
        this.packageMapper = packageMapper;
        this.packageRuleMapper = packageRuleMapper;
        this.ruleMapper = ruleMapper;
        this.dynamicScoreMapper = dynamicScoreMapper;
        this.scoreBandMapper = scoreBandMapper;
        this.strategyDefMapper = strategyDefMapper;
        this.ruleStrategyMapper = ruleStrategyMapper;
        this.scoreBandStrategyMapper = scoreBandStrategyMapper;
        this.objectMapper = objectMapper;
        this.configCache = configCache;
    }

    @Override
    public RulePackageDefinition load(long packageId) {
        return configCache.getOrLoad("RULE_PACKAGE", String.valueOf(packageId), id -> loadUncached(Long.parseLong(id)));
    }

    private RulePackageDefinition loadUncached(long packageId) {
        RulePackagePO pkg = packageMapper.selectById(packageId);
        if (pkg == null) {
            log.warn("规则包节点引用的规则包不存在: packageId={}", packageId);
            return null;
        }
        // 已下线规则包：运行期降级（返回 null，由节点处理器记录原因 R6.4/R6.6）
        if (pkg.getStatus() != null && "DISABLED".equalsIgnoreCase(pkg.getStatus())) {
            log.warn("规则包节点引用的规则包已下线: packageId={}", packageId);
            return null;
        }

        List<RulePackageRulePO> relations = packageRuleMapper.selectList(
                new LambdaQueryWrapper<RulePackageRulePO>()
                        .eq(RulePackageRulePO::getRulePackageId, packageId));

        TriggerMode mode = parseTriggerMode(pkg.getTriggerMode());

        List<RulePackageRule> rules = new ArrayList<>(relations.size());
        for (RulePackageRulePO rel : relations) {
            RuleV2SamplePO rulePo = ruleMapper.selectById(rel.getRuleV2Id());
            if (rulePo == null) {
                continue;
            }
            // 规则三态过滤：仅 ONLINE/TRIAL_RUN 进入执行集，跳过 OFFLINE（R7.3/R7.4）
            RuleRuntimeStatus status = RuleRuntimeStatus.parse(rulePo.getStatus());
            if (!status.executable()) {
                continue;
            }
            int priority = rel.getPriority() == null ? 0 : rel.getPriority();
            // 命中模式加载规则绑定策略；评分模式策略由区间映射产出，规则级策略无需加载
            List<StrategyItem> strategies = mode == TriggerMode.HIT
                    ? loadRuleStrategies(rulePo.getId())
                    : List.of();
            rules.add(toPackageRule(rulePo, priority, strategies, status.trialRun()));
        }

        if (mode == TriggerMode.SCORE) {
            List<ScoreBand> bands = loadScoreBands(packageId);
            WarnScoreOp warnOp = parseWarnOp(pkg.getWarnScoreOp());
            boolean warnEnabled = pkg.getWarnScoreEnabled() != null && pkg.getWarnScoreEnabled() == 1
                    && warnOp != null && pkg.getWarnScoreThreshold() != null;
            if (warnEnabled) {
                return RulePackageDefinition.score(packageId, rules, bands, warnOp,
                        pkg.getWarnScoreThreshold());
            }
            return RulePackageDefinition.score(packageId, rules, bands);
        }
        return RulePackageDefinition.hit(packageId, rules);
    }

    /** rule_v2 → 执行侧 RulePackageRule（含编译表达式、决策语义、绑定策略、评分配置、三态标识）。 */
    private RulePackageRule toPackageRule(RuleV2SamplePO po, int priority, List<StrategyItem> strategies,
                                          boolean trialRun) {
        boolean shortCircuited = po.getShortCircuited() != null && po.getShortCircuited() == 1;
        Decision decision = riskLevelToDecision(po.getRiskLevelCode());
        ScoreRule scoreRule = loadScoreRule(po);
        return new RulePackageRule(
                po.getId(),
                1,
                priority,
                po.getCompiledExpr(),
                decision,
                shortCircuited,
                strategies,
                scoreRule,
                trialRun);
    }

    /** 加载某规则的评分配置（基础分 + 动态分区间）。 */
    private ScoreRule loadScoreRule(RuleV2SamplePO po) {
        List<RuleDynamicScorePO> bands = dynamicScoreMapper.selectList(
                new LambdaQueryWrapper<RuleDynamicScorePO>()
                        .eq(RuleDynamicScorePO::getRuleV2Id, po.getId())
                        .orderByAsc(RuleDynamicScorePO::getOrderNo));
        List<ScoreDynamicBand> dynamicBands = new ArrayList<>(bands.size());
        for (RuleDynamicScorePO b : bands) {
            dynamicBands.add(new ScoreDynamicBand(
                    b.getIndicatorRefName(),
                    b.getLower(),
                    b.getUpper(),
                    b.getLowerInclusive() == null || b.getLowerInclusive() == 1,
                    b.getUpperInclusive() != null && b.getUpperInclusive() == 1,
                    b.getScore()));
        }
        BigDecimal base = po.getBaseScore() == null ? BigDecimal.ZERO : po.getBaseScore();
        return new ScoreRule(po.getId(), base, dynamicBands);
    }

    /** 加载规则包评分分值区间（按 order_no 升序），含区间绑定策略。 */
    private List<ScoreBand> loadScoreBands(long packageId) {
        List<RulePackageScoreBandPO> pos = scoreBandMapper.selectList(
                new LambdaQueryWrapper<RulePackageScoreBandPO>()
                        .eq(RulePackageScoreBandPO::getRulePackageId, packageId)
                        .orderByAsc(RulePackageScoreBandPO::getOrderNo));
        List<ScoreBand> bands = new ArrayList<>(pos.size());
        for (RulePackageScoreBandPO po : pos) {
            bands.add(new ScoreBand(
                    po.getLower(),
                    po.getUpper(),
                    po.getLowerInclusive() == null || po.getLowerInclusive() == 1,
                    po.getUpperInclusive() != null && po.getUpperInclusive() == 1,
                    po.getRiskLevelCode(),
                    loadScoreBandStrategies(po.getId())));
        }
        return bands;
    }

    /** 命中模式：加载某规则绑定的策略列表（rule_strategy + strategy_def）。 */
    private List<StrategyItem> loadRuleStrategies(long ruleV2Id) {
        List<RuleStrategyReadPO> bindings = ruleStrategyMapper.selectList(
                new LambdaQueryWrapper<RuleStrategyReadPO>()
                        .eq(RuleStrategyReadPO::getRuleV2Id, ruleV2Id));
        List<StrategyItem> items = new ArrayList<>(bindings.size());
        for (RuleStrategyReadPO b : bindings) {
            StrategyDefReadPO def = strategyDefMapper.selectById(b.getStrategyDefId());
            if (def == null || isDisabled(def)) {
                continue;
            }
            int priority = b.getPriority() == null ? 0 : b.getPriority();
            items.add(toStrategyItem(def, priority));
        }
        return items;
    }

    /** 评分模式：加载某分值区间绑定的策略列表（score_band_strategy + strategy_def）。 */
    private List<StrategyItem> loadScoreBandStrategies(long scoreBandId) {
        List<ScoreBandStrategyReadPO> bindings = scoreBandStrategyMapper.selectList(
                new LambdaQueryWrapper<ScoreBandStrategyReadPO>()
                        .eq(ScoreBandStrategyReadPO::getScoreBandId, scoreBandId));
        List<StrategyItem> items = new ArrayList<>(bindings.size());
        for (ScoreBandStrategyReadPO b : bindings) {
            StrategyDefReadPO def = strategyDefMapper.selectById(b.getStrategyDefId());
            if (def == null || isDisabled(def)) {
                continue;
            }
            // 区间策略无规则级优先级语义，priority 取 0
            items.add(toStrategyItem(def, 0));
        }
        return items;
    }

    /** strategy_def → 引擎侧 StrategyItem（限额类型/阈值从 params_json 解析）。 */
    private StrategyItem toStrategyItem(StrategyDefReadPO def, int priority) {
        StrategyCategory category = parseCategory(def.getCategory());
        Map<String, Object> params = parseParams(def.getParamsJson());
        String limitType = null;
        BigDecimal threshold = null;
        if (category == StrategyCategory.CONTROL_LIMIT) {
            Object lt = params.get("limitType");
            limitType = lt == null ? null : String.valueOf(lt);
            threshold = toBigDecimal(params.get("threshold"));
        }
        return new StrategyItem(category, def.getCode(), priority, limitType, threshold, params);
    }

    private boolean isDisabled(StrategyDefReadPO def) {
        return def.getStatus() != null && "DISABLED".equalsIgnoreCase(def.getStatus());
    }

    private Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            return m == null ? Map.of() : m;
        } catch (Exception e) {
            log.warn("策略 params_json 解析失败，按空参数处理: 原因={}", e.getMessage());
            return Map.of();
        }
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private StrategyCategory parseCategory(String v) {
        try {
            return StrategyCategory.valueOf(v);
        } catch (Exception e) {
            // 未知类别按通知处理（最弱处置语义，仅记录），避免加载失败中断
            return StrategyCategory.NOTIFY;
        }
    }

    private TriggerMode parseTriggerMode(String v) {
        try {
            return v == null ? TriggerMode.HIT : TriggerMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            return TriggerMode.HIT;
        }
    }

    private WarnScoreOp parseWarnOp(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return WarnScoreOp.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 命中模式规则的命中决策：由规则风险等级编码经默认风险等级映射推导。
     * 无风险等级时按 REVIEW（保守，确保「命中即有处置倾向」），与试运行适配器口径一致。
     */
    private Decision riskLevelToDecision(String riskLevelCode) {
        if (riskLevelCode == null || riskLevelCode.isBlank()) {
            return Decision.REVIEW;
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
    }
}
